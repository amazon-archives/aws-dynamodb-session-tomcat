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
package com.amazonaws.services.dynamodb.sessionmanager.util;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodb.sessionmanager.DynamoDBSessionManager;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.test.AWSIntegrationTestBase;
import com.amazonaws.test.AWSTestBase;
import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.apache.catalina.startup.Tomcat;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/*
** Need to setup the AWS connection properties file.
**    System.getProperty("user.home") + "/.aws/awsTestAccount.properties"
**        accessKey=ABCDEFGHI12345678
**        secretKey=ABCDEFG123456789HIJKL
**
** You can test different tomcat versions by using different profiles.
**    tomcat7 and tomcat8 are currently set; where tomcat8 is default.
**
** To run on tomcat 7
**    mvn clean package -Dtest=TomcatIntegrationTest -Ptomcat7
**
*/
public class TomcatIntegrationTest extends AWSIntegrationTestBase {
    private static final Log logger = LogFactory.getLog(TomcatIntegrationTest.class);

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
        dynamo = new AmazonDynamoDBClient(getCredentials());

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
        try {
            dynamo.deleteTable(sessionTableName);
        }
        catch (Exception e) {
            logger.warn("Unable to delete table[" + sessionTableName + "]");
        }
    }

    @AfterClass
    public static void tearDownFixture() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    /** Test that a context can be retrieved by Tomcat */
    @Test
    public void testGetContextFromTomcat() throws Exception {
        DynamoDBSessionManager sessionManager = new DynamoDBSessionManager();
        sessionManager.setAwsCredentialsFile(System.getProperty("user.home") + "/.aws/awsTestAccount.properties");
        sessionManager.setTable(sessionTableName);

        webapp.setManager(sessionManager);

        Context context = TomcatUtils.getContext(sessionManager);
        assertNotNull(context);
    }
}
