/*
 * Copyright 2013 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.services.dynamodb.sessionmanager;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.dynamodb.sessionmanager.converters.SessionConverter;
import com.amazonaws.services.dynamodb.sessionmanager.util.ConfigUtils;
import com.amazonaws.services.dynamodb.sessionmanager.util.DynamoUtils;
import com.amazonaws.services.dynamodb.sessionmanager.util.TomcatUtils;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.util.Tables;
import com.amazonaws.util.StringUtils;

import org.apache.catalina.Context;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.session.PersistentManagerBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.File;

/**
 * Tomcat persistent session manager implementation that uses Amazon DynamoDB to store HTTP session
 * data.
 */
public class DynamoDBSessionManager extends PersistentManagerBase {
    private static final Log logger = LogFactory.getLog(DynamoDBSessionManager.class);

    private final String version;
    private final String userAgent;
    private final String name;
    private final String regionId;

    private final boolean deleteCorruptSessions;
    private final boolean createIfNotExist;
    private final boolean disableReaper;

    private final long readCapacityUnits;
    private final long writeCapacityUnits;

    private File credentialsFile;
    private String accessKey;
    private String secretKey;
    private String tableName;

    private String proxyHost;    // This is not set, ever...
    private Integer proxyPort;   // This is not set, ever...

    // This makes sure the TomcatUtils is only used once.
    private Context appContext;

    public DynamoDBSessionManager() {
        setSaveOnRestart(ConfigUtils.getBoolean(ConfigUtils.SAVE_ON_RESTART, true));

        // MaxIdleBackup controls when sessions are persisted to the store
        setMaxIdleBackup(ConfigUtils.getInt(ConfigUtils.MAX_IDLE_BACKUP, 30));

        // Table name is a constant because of the Table Annotation in DynamoSessionItem
        this.tableName = ConfigUtils.DEFAULT_TABLE_NAME;

        this.version = ConfigUtils.getString(ConfigUtils.VERSION);
        this.userAgent = ConfigUtils.getString(ConfigUtils.USER_AGENT) + "/" + this.version;
        this.name = ConfigUtils.getString(ConfigUtils.NAME) + "/" + this.version;
        this.regionId = ConfigUtils.getString(ConfigUtils.REGION_ID, "us-east-1");

        this.deleteCorruptSessions = ConfigUtils.getBoolean(ConfigUtils.DELETE_CORRUPTED_SESSIONS, Boolean.TRUE);
        this.createIfNotExist = ConfigUtils.getBoolean(ConfigUtils.CREATE_IF_NOT_EXIST, Boolean.TRUE);
        this.disableReaper = ConfigUtils.getBoolean(ConfigUtils.DISABLE_REAPER, Boolean.FALSE);

        this.readCapacityUnits = ConfigUtils.getLong(ConfigUtils.READ_CAPACITY_UNITS, 10);
        this.writeCapacityUnits = ConfigUtils.getLong(ConfigUtils.WRITE_CAPACITY_UNITS, 5);
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setTableName(final String tableName) { this.tableName = tableName; }
    public void setAwsAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }
    public void setAwsSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
    public void setAwsCredentialsFile(String credentialsFile) {
        this.credentialsFile = new File(credentialsFile);
    }


    @Override
    protected void initInternal() throws LifecycleException {
        AmazonDynamoDBClient dynamoClient = createDynamoClient();
        initDynamoTable(dynamoClient);

        DynamoSessionStorage sessionStorage = createSessionStorage(dynamoClient);
        DynamoDBSessionStore store = new DynamoDBSessionStore(sessionStorage, this.name,
                this.deleteCorruptSessions);

        setStore(store);

        if (this.disableReaper) {
            logger.info("ExpiredSessionReaperExecutor has been disabled.");
        }
        else {
            new ExpiredSessionReaperExecutor(new ExpiredSessionReaper(sessionStorage));
        }
    }

    private AmazonDynamoDBClient createDynamoClient() {
        AWSCredentialsProvider credentialsProvider = initCredentials();
        ClientConfiguration clientConfiguration = initClientConfiguration();

        AmazonDynamoDBClient dynamoClient = new AmazonDynamoDBClient(credentialsProvider, clientConfiguration);
        if (this.regionId != null) {
            dynamoClient.setRegion(RegionUtils.getRegion(this.regionId));
        }

        return dynamoClient;
    }

    private AWSCredentialsProvider initCredentials() {
        // Attempt to use any credentials specified in context.xml first
        if (credentialsExistInContextConfig()) {
            // Fail fast if credentials aren't valid as user has likely made a configuration mistake
            if (credentialsInContextConfigAreValid()) {
                throw new AmazonClientException("Incomplete AWS security credentials specified in context.xml.");
            }
            logger.debug("Using AWS access key ID and secret key from context.xml");
            return new StaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        }

        // Use any explicitly specified credentials properties file next
        if (credentialsFile != null) {
            try {
                logger.debug("Reading security credentials from properties file: " + credentialsFile);
                PropertiesCredentials credentials = new PropertiesCredentials(credentialsFile);
                logger.debug("Using AWS credentials from file: " + credentialsFile);
                return new StaticCredentialsProvider(credentials);
            } catch (Exception e) {
                throw new AmazonClientException(
                        "Unable to read AWS security credentials from file specified in context.xml: "
                                + credentialsFile,
                        e);
            }
        }

        // Fall back to the default credentials chain provider if credentials weren't explicitly set
        AWSCredentialsProvider defaultChainProvider = new DefaultAWSCredentialsProviderChain();
        if (defaultChainProvider.getCredentials() == null) {
            logger.debug("Loading security credentials from default credentials provider chain.");
            throw new AmazonClientException("Unable to find AWS security credentials.  "
                    + "Searched JVM system properties, OS env vars, and EC2 instance roles.  "
                    + "Specify credentials in Tomcat's context.xml file or put them in one of the places mentioned above.");
        }
        logger.debug("Using default AWS credentials provider chain to load credentials");
        return defaultChainProvider;
    }

    /**
     * @return True if the user has set their AWS credentials either partially or completely in
     *         context.xml. False otherwise
     */
    private boolean credentialsExistInContextConfig() {
        return accessKey != null || secretKey != null;
    }

    /**
     * @return True if both the access key and secret key were set in context.xml config. False
     *         otherwise
     */
    private boolean credentialsInContextConfigAreValid() {
        return StringUtils.isNullOrEmpty(accessKey) || StringUtils.isNullOrEmpty(secretKey);
    }

    private ClientConfiguration initClientConfiguration() {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setUserAgent(this.userAgent);

        // Attempt to use an explicit proxy configuration
        if (proxyHost != null && proxyPort != null) {
            logger.debug("Using proxy host and port from context.xml");
            clientConfiguration.withProxyHost(proxyHost).withProxyPort(proxyPort);
        }
        else if (proxyHost != null || proxyPort != null) {
            throw new AmazonClientException("Incomplete proxy settings specified in context.xml."
                    + " Both proxy hot and proxy port needs to be specified");
        }

        return clientConfiguration;
    }

    private void initDynamoTable(AmazonDynamoDBClient dynamo) {
        boolean tableExists = Tables.doesTableExist(dynamo, this.tableName);

        if (!tableExists && !createIfNotExist) {
            throw new AmazonClientException("Session table '" + tableName + "' does not exist, "
                    + "and automatic table creation has been disabled in context.xml");
        }

        if (!tableExists) {
            DynamoUtils.createSessionTable(dynamo, this.tableName, this.readCapacityUnits, this.writeCapacityUnits);
        }

        Tables.waitForTableToBecomeActive(dynamo, this.tableName);
    }

    private DynamoSessionStorage createSessionStorage(AmazonDynamoDBClient dynamoClient) {
        DynamoDBMapper dynamoMapper = DynamoUtils.createDynamoMapper(dynamoClient, tableName);
        return new DynamoSessionStorage(dynamoMapper, getSessionConverter());
    }

    private SessionConverter getSessionConverter() {
        ClassLoader classLoader = this.getAppContext().getLoader().getClassLoader();
        return SessionConverter.createDefaultSessionConverter(this, classLoader);
    }


    /*
    ** This allows for the code base to work with Tomcat 7 and 8.
    */
    public Context getAppContext() {
        if (appContext == null)
            appContext = TomcatUtils.getContext(this);

        return appContext;
    }
}
