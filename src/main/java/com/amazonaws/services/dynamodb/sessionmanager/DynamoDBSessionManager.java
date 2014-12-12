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

import org.apache.catalina.LifecycleException;
import org.apache.catalina.session.PersistentManagerBase;
import org.apache.juli.logging.Log;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.dynamodb.sessionmanager.util.DynamoUtils;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;

/**
 * Tomcat 7.0 persistent session manager implementation that uses Amazon
 * DynamoDB to store HTTP session data.
 */
public class DynamoDBSessionManager extends PersistentManagerBase {

    private static final String DEFAULT_TABLE_NAME = "Tomcat_SessionState";

    private static final String name = "AmazonDynamoDBSessionManager";
    private static final String info = name + "/1.0";

    private String regionId = "us-east-1";
    private String endpoint;
    private File credentialsFile;
    private String accessKey;
    private String secretKey;
    private long readCapacityUnits = 10;
    private long writeCapacityUnits = 5;
    private boolean createIfNotExist = true;
    private String tableName = DEFAULT_TABLE_NAME;

    private final DynamoDBSessionStore dynamoSessionStore;

    private ExpiredSessionReaper expiredSessionReaper;

    private static Log logger;


    public DynamoDBSessionManager() {
        dynamoSessionStore = new DynamoDBSessionStore();
        setStore(dynamoSessionStore);
        setSaveOnRestart(true);

        // MaxInactiveInterval controls when sessions are removed from the store
        setMaxInactiveInterval(60 * 60 * 2); // 2 hours

        // MaxIdleBackup controls when sessions are persisted to the store
        setMaxIdleBackup(30); // 30 seconds
    }

    @Override
    public String getName() {
        return name;
    }

    //
    // Context.xml Configuration Members
    //

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


    //
    // Private Interface
    //

    @Override
    protected void initInternal() throws LifecycleException {
        this.setDistributable(true);

        // Grab the container's logger
        logger = getContext().getLogger();

        AWSCredentialsProvider credentialsProvider = initCredentials();
        AmazonDynamoDBClient dynamo = new AmazonDynamoDBClient(credentialsProvider);
        if (this.regionId != null) dynamo.setRegion(RegionUtils.getRegion(this.regionId));
        if (this.endpoint != null) dynamo.setEndpoint(this.endpoint);

        initDynamoTable(dynamo);

        // init session store
        dynamoSessionStore.setDynamoClient(dynamo);
        dynamoSessionStore.setSessionTableName(this.tableName);

        expiredSessionReaper = new ExpiredSessionReaper(dynamo, tableName, this.maxInactiveInterval);
    }

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        super.stopInternal();
        if (expiredSessionReaper != null) expiredSessionReaper.shutdown();
    }

    private void initDynamoTable(AmazonDynamoDBClient dynamo) {
        boolean tableExists = DynamoUtils.doesTableExist(dynamo, this.tableName);

        if (!tableExists && !createIfNotExist) {
            throw new AmazonClientException("Session table '" + tableName + "' does not exist, "
                    + "and automatic table creation has been disabled in context.xml");
        }

        if (!tableExists) DynamoUtils.createSessionTable(dynamo, this.tableName,
                this.readCapacityUnits, this.writeCapacityUnits);

        DynamoUtils.waitForTableToBecomeActive(dynamo, this.tableName);
    }

    private AWSCredentialsProvider initCredentials() {
        // Attempt to use any explicitly specified credentials first
        if (accessKey != null || secretKey != null) {
            getContext().getLogger().debug("Reading security credentials from context.xml");
            if (accessKey == null || secretKey == null) {
                throw new AmazonClientException("Incomplete AWS security credentials specified in context.xml.");
            }
            getContext().getLogger().debug("Using AWS access key ID and secret key from context.xml");
            return new StaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        }

        // Use any explicitly specified credentials properties file next
        if (credentialsFile != null) {
            try {
                getContext().getLogger().debug("Reading security credentials from properties file: " + credentialsFile);
                PropertiesCredentials credentials = new PropertiesCredentials(credentialsFile);
                getContext().getLogger().debug("Using AWS credentials from file: " + credentialsFile);
                return new StaticCredentialsProvider(credentials);
            } catch (Exception e) {
                throw new AmazonClientException(
                        "Unable to read AWS security credentials from file specified in context.xml: " + credentialsFile, e);
            }
        }

        // Fall back to the default credentials chain provider if credentials weren't explicitly set
        AWSCredentialsProvider defaultChainProvider = new DefaultAWSCredentialsProviderChain();
        if (defaultChainProvider.getCredentials() == null) {
            getContext().getLogger().debug("Loading security credentials from default credentials provider chain.");
            throw new AmazonClientException(
                    "Unable find AWS security credentials.  " +
                    "Searched JVM system properties, OS env vars, and EC2 instance roles.  " +
                    "Specify credentials in Tomcat's context.xml file or put them in one of the places mentioned above.");
        }
        getContext().getLogger().debug("Using default AWS credentials provider chain to load credentials");
        return defaultChainProvider;
    }


    //
    // Logger Utility Functions
    //

    public static void debug(String s) {
        logger.debug(s);
    }

    public static void warn(String s) {
        logger.warn(s);
    }

    public static void warn(String s, Exception e) {
        logger.warn(s, e);
    }

    public static void error(String s) {
        logger.error(s);
    }

    public static void error(String s, Exception e) {
        logger.error(s, e);
    }
}
