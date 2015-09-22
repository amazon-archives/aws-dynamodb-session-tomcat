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

import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.amazonaws.services.dynamodb.sessionmanager.converters.SessionConverter;
import com.amazonaws.services.dynamodb.sessionmanager.util.DynamoUtils;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.util.Tables;
import com.amazonaws.test.AWSTestBase;

/**
 * Base class for tests interacting directly with DynamoDB. Creates a unique table per test class
 */
public class SessionStorageIntegrationTestBase extends AWSTestBase {

    private static AmazonDynamoDBClient dynamoClient;
    private static DynamoDBMapper dynamoMapper;
    private static String tableName;

    @BeforeClass
    public static final void baseSetupFixture() throws Exception {
        setUpCredentials();
        dynamoClient = new AmazonDynamoDBClient(credentials);
        tableName = getUniqueTableName();
        DynamoUtils.createSessionTable(dynamoClient, tableName, 10L, 10L);
        Tables.waitForTableToBecomeActive(dynamoClient, tableName);
        dynamoMapper = DynamoUtils.createDynamoMapper(dynamoClient, tableName);
    }

    /**
     * Delete all items in the table before running the next test. Faster than creating a new table
     * for every test.
     */
    @After
    public void baseTearDown() {
        List<FailedBatch> failedBatches = dynamoMapper
                .batchDelete(dynamoMapper.scan(DynamoSessionItem.class, new DynamoDBScanExpression()));
        // If we for some reason couldn't delete all items bail out so we don't affect other tests
        assertThat(failedBatches, empty());
    }

    @AfterClass
    public static final void baseTearDownFixture() {
        dynamoClient.deleteTable(tableName);
    }

    private static String getUniqueTableName() {
        return String.format("%s%s-%d", SessionStorageIntegrationTestBase.class.getSimpleName(), "IntegrationTest",
                System.currentTimeMillis());
    }

    protected DynamoSessionStorage createSessionStorage(SessionConverter sessionConverter) {
        return new DynamoSessionStorage(dynamoMapper, sessionConverter);
    }

}
