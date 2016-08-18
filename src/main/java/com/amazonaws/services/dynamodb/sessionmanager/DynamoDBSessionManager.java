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

import java.io.File;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.session.PersistentManagerBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.dynamodb.sessionmanager.converters.SessionConverter;
import com.amazonaws.services.dynamodb.sessionmanager.util.DynamoUtils;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.util.Tables;
import com.amazonaws.util.StringUtils;

/**
 * Tomcat persistent session manager implementation that uses Amazon DynamoDB to store HTTP session
 * data.
 */
public class DynamoDBSessionManager extends PersistentManagerBase {

    public static final String DEFAULT_TABLE_NAME = "Tomcat_SessionState";

    private static final String USER_AGENT = "DynamoSessionManager/2.0.1";
    private static final String name = "AmazonDynamoDBSessionManager";
    private static final String info = name + "/2.0.1";

    private String regionId = "us-east-1";
    private String endpoint;
    private File credentialsFile;
    private String accessKey;
    private String secretKey;
    private long readCapacityUnits = 10;
    private long writeCapacityUnits = 5;
    private boolean createIfNotExist = true;
    private String tableName = DEFAULT_TABLE_NAME;
    private String proxyHost;
    private Integer proxyPort;
    private boolean deleteCorruptSessions = false;

    private static final Log logger = LogFactory.getLog(DynamoDBSessionManager.class);

    public DynamoDBSessionManager() {
        setSaveOnRestart(true);

        // MaxIdleBackup controls when sessions are persisted to the store
        setMaxIdleBackup(30); // 30 seconds
    }

    @Override
    public String getInfo() {
        return info;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setAwsAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public void setAwsSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setAwsCredentialsFile(String credentialsFile) {
        this.credentialsFile = new File(credentialsFile);
    }

    public void setTable(String table) {
        this.tableName = table;
    }

    public void setReadCapacityUnits(int readCapacityUnits) {
        this.readCapacityUnits = readCapacityUnits;
    }

    public void setWriteCapacityUnits(int writeCapacityUnits) {
        this.writeCapacityUnits = writeCapacityUnits;
    }

    public void setCreateIfNotExist(boolean createIfNotExist) {
        this.createIfNotExist = createIfNotExist;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public void setDeleteCorruptSessions(boolean deleteCorruptSessions) {
        this.deleteCorruptSessions = deleteCorruptSessions;
    }

    @Override
    protected void initInternal() throws LifecycleException {
        this.setDistributable(true);

        AmazonDynamoDBClient dynamoClient = createDynamoClient();
        initDynamoTable(dynamoClient);
        DynamoSessionStorage sessionStorage = createSessionStorage(dynamoClient);
        setStore(new DynamoDBSessionStore(sessionStorage, deleteCorruptSessions));
        new ExpiredSessionReaperExecutor(new ExpiredSessionReaper(sessionStorage));
    }

    private AmazonDynamoDBClient createDynamoClient() {
        AWSCredentialsProvider credentialsProvider = initCredentials();
        ClientConfiguration clientConfiguration = initClientConfiguration();
        AmazonDynamoDBClient dynamoClient = new AmazonDynamoDBClient(credentialsProvider, clientConfiguration);
        if (this.regionId != null) {
            dynamoClient.setRegion(RegionUtils.getRegion(this.regionId));
        }
        if (this.endpoint != null) {
            dynamoClient.setEndpoint(this.endpoint);
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
        clientConfiguration.setUserAgent(USER_AGENT);

        // Attempt to use an explicit proxy configuration
        if (proxyHost != null || proxyPort != null) {
            logger.debug("Reading proxy settings from context.xml");
            if (proxyHost == null || proxyPort == null) {
                throw new AmazonClientException("Incomplete proxy settings specified in context.xml."
                        + " Both proxy hot and proxy port needs to be specified");
            }
            logger.debug("Using proxy host and port from context.xml");
            clientConfiguration.withProxyHost(proxyHost).withProxyPort(proxyPort);
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
        ClassLoader classLoader = getAssociatedContext().getLoader().getClassLoader();
        return SessionConverter.createDefaultSessionConverter(this, classLoader);
    }

    /**
     * To be compatible with Tomcat7 we have to call the getContainer method rather than getContext.
     * The cast is safe as it only makes sense to use a session manager within the context of a
     * webapp, the Tomcat 8 version of getContainer just delegates to getContext. When Tomcat7 is no
     * longer supported this can be changed to getContext
     *
     * @return The context this manager is associated with
     */
    // TODO Inline this method with getManager().getContext() when Tomcat7 is no longer supported
    private Context getAssociatedContext() {
        try {
            return (Context) getContainer();
        } catch (ClassCastException e) {
            logger.fatal("Unable to cast " + getClass().getName() + " to a Context."
                    + " DynamoDB SessionManager can only be used with a Context");
            throw new IllegalStateException(e);
        }
    }
}
