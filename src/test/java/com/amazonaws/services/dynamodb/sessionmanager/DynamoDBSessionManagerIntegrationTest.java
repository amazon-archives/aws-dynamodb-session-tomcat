/*
 * Copyright 2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.test.AWSTestBase;

import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.apache.catalina.startup.Tomcat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DynamoDBSessionManagerIntegrationTest extends AWSTestBase {

    private static final String SESSION_ID = "1234";
    private static final int MAX_IDLE_BACKUP_SECONDS = 1;
    private static final String ATTR_NAME = "someAttr";

    private static AmazonDynamoDBClient dynamo;
    private static Tomcat tomcat;
    private static Context webapp;

    private String sessionTableName;

    /** Starts up an embedded Tomcat process for testing. */
    @BeforeClass
    public static void setupFixture() throws Exception {
        setUpCredentials();
        dynamo = new AmazonDynamoDBClient(credentials);

        String workingDir = System.getProperty("java.io.tmpdir");
        File webappDirectory = Files.createTempDirectory(Paths.get(workingDir), null).toFile();
        webappDirectory.deleteOnExit();

        tomcat = new Tomcat();
        tomcat.setPort(0);
        tomcat.setBaseDir(workingDir);
        tomcat.getHost().setAppBase(workingDir);
        tomcat.getHost().setAutoDeploy(true);
        tomcat.getHost().setDeployOnStartup(true);
        webapp = tomcat.addWebapp("/", webappDirectory.getAbsolutePath());

        tomcat.start();
    }

    @Before
    public void setup() {
        sessionTableName = "sessions-test-" + System.currentTimeMillis();
    }

    @After
    public void tearDown() {
        dynamo.deleteTable(sessionTableName);
    }

    @AfterClass
    public static void tearDownFixture() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    /** Tests that we can use explicitly provided credentials. */
    @Test
    public void testExplicitCredentials() throws Exception {
        assertFalse(doesTableExist(sessionTableName));

        DynamoDBSessionManager sessionManager = new DynamoDBSessionManager();
        configureWithExplicitCredentials(sessionManager);

        assertTrue(doesTableExist(sessionTableName));
    }

    /** Tests that we can load credentials from a configured properties file. */
    @Test
    public void testCredentialsFile() throws Exception {
        assertFalse(doesTableExist(sessionTableName));

        DynamoDBSessionManager sessionManager = new DynamoDBSessionManager();
        sessionManager.setAwsCredentialsFile(System.getProperty("user.home") + "/.aws/awsTestAccount.properties");
        sessionManager.setTable(sessionTableName);
        webapp.setManager(sessionManager);

        assertTrue(doesTableExist(sessionTableName));
    }

    /**
     * Tests the roundtrip journey of a session from memory to Dynamo and back to memory.
     */
    @Test
    public void sessionSwappedOutToDynamo_IsUnchangedWhenSwappedBackIn() throws Exception {
        final String attrValue = "SOME_VALUE";

        TestDynamoDBSessionManager sessionManager = new TestDynamoDBSessionManager();
        sessionManager.setDeleteCorruptSessions(true);
        // MaxIdleSwap needs to be set too as we want it completely out of memory before loading
        sessionManager.setMaxIdleBackup(MAX_IDLE_BACKUP_SECONDS);
        sessionManager.setMaxIdleSwap(MAX_IDLE_BACKUP_SECONDS);
        configureWithExplicitCredentials(sessionManager);

        Session originalSession = sessionManager.createSession(SESSION_ID);
        final long originalCreationTime = originalSession.getCreationTime();
        originalSession.getSession().setAttribute(ATTR_NAME, attrValue);

        // Make sure it's out of Tomcat's in memory store and persisted to Dynamo
        Thread.sleep(TimeUnit.MILLISECONDS.convert(MAX_IDLE_BACKUP_SECONDS + 1, TimeUnit.SECONDS));
        sessionManager.reallyProcessExpires();

        // Force session manager to load back in non-expired sessions into memory and validate
        // nothing important has changed
        sessionManager.load();
        assertEquals(attrValue, sessionManager.getSession(SESSION_ID).get(ATTR_NAME));
        assertEquals(originalCreationTime, sessionManager.getCreationTimestamp(SESSION_ID));
    }

    /**
     * Bug in the deserialization of sessions was causing persisted sessions loaded via the
     * processExpires method to replace the active session in memory by incorrectly registering it
     * with the manager. This tests makes sure that any sessions loaded by process expires do not
     * affect the attributes of active sessions.
     *
     * @see <a href="https://github.com.aws/aws-dynamodb-session-tomcat/pull/19">PR #19</a>
     */
    @Test
    public void swappedOutSessionsDoNotReplaceActiveSessionDuringProcessExpires() throws InterruptedException {
        TestDynamoDBSessionManager sessionManager = new TestDynamoDBSessionManager();
        configureWithExplicitCredentials(sessionManager);

        final String originalAttrValue = "1";
        final String newAttrValue = "2";

        // Create a session and idle it so it's persisted to Dynamo
        sessionManager.setMaxIdleBackup(MAX_IDLE_BACKUP_SECONDS);
        Session newSession = sessionManager.createSession(SESSION_ID);
        setSessionAttribute(newSession, ATTR_NAME, originalAttrValue);
        Thread.sleep(TimeUnit.MILLISECONDS.convert(MAX_IDLE_BACKUP_SECONDS + 1, TimeUnit.SECONDS));
        // Force session manager to persist sessions that have idled
        sessionManager.reallyProcessExpires();

        // Set a new value for the attribute that will only exist in memory
        setSessionAttribute(newSession, ATTR_NAME, newAttrValue);
        // Force session manager to load in persisted sessions to prune them if expired
        sessionManager.reallyProcessExpires();

        // The active session in memory should not be affected by the sessions loaded during
        // processExpires
        assertEquals(newAttrValue, sessionManager.getSessionAttribute(SESSION_ID, ATTR_NAME));
    }

    private void configureWithExplicitCredentials(DynamoDBSessionManager sessionManager) {
        sessionManager.setAwsAccessKey(credentials.getAWSAccessKeyId());
        sessionManager.setAwsSecretKey(credentials.getAWSSecretKey());
        sessionManager.setTable(sessionTableName);
        webapp.setManager(sessionManager);
    }

    private void setSessionAttribute(Session newSession, String attrName, Object obj) {
        newSession.access();
        newSession.getSession().setAttribute(attrName, obj);
        newSession.endAccess();
    }

    /**
     * Returns true if the specified table exists, and is active and ready for use.
     */
    private static boolean doesTableExist(String tableName) {
        try {
            TableDescription table = dynamo.describeTable(new DescribeTableRequest().withTableName(tableName))
                    .getTable();
            return "ACTIVE".equals(table.getTableStatus());
        } catch (AmazonServiceException ase) {
            if (ase.getErrorCode().equals("ResourceNotFoundException")) {
                return false;
            }
            throw ase;
        }
    }

    /**
     * Subclassed DynamoDBSessionManager to allow explicit calling of processExpires
     */
    private class TestDynamoDBSessionManager extends DynamoDBSessionManager {

        @Override
        public void processExpires() {
            // Do nothing, processExpires is called internally periodically and we want to prevent
            // that so we can call processExpires when we need to in the tests
        }

        public void reallyProcessExpires() {
            super.processExpires();
        }
    }

}
