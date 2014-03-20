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

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.dynamodb.sessionmanager.util.DynamoUtils;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.*;

/**
 * A background process to periodically scan (once every 12 hours, with an
 * initial random jitter) and remove any expired session data from the session
 * table in Amazon DynamoDB.
 */
public class ExpiredSessionReaper {

    private AmazonDynamoDBClient dynamo;
    private String tableName;
    private long expirationTimeInMillis;
    private ScheduledThreadPoolExecutor executor;
    private static String[] ATTRIBUTES_TO_GET = {
        SessionTableAttributes.SESSION_ID_KEY, 
        SessionTableAttributes.SESSION_SEQ_KEY,
        SessionTableAttributes.LAST_UPDATED_AT_ATTRIBUTE};


    /**
     * Constructs and immediately starts an ExpiredSessionReaper.
     *
     * @param dynamo
     *            The client to use when accessing Amazon DynamoDB.
     * @param tableName
     *            The name of the DynamoDB table containing session information.
     * @param expirationTimeInMillis
     *            The time, in milliseconds, after which a session is considered
     *            expired and should be removed from the session table.
     */
    public ExpiredSessionReaper(AmazonDynamoDBClient dynamo, String tableName, long expirationTimeInMillis) {
        this.dynamo = dynamo;
        this.tableName = tableName;
        this.expirationTimeInMillis = expirationTimeInMillis;

        int initialDelay = new Random().nextInt(5) + 1;
        executor = new ScheduledThreadPoolExecutor(1, new ExpiredSessionReaperThreadFactory());
        executor.scheduleAtFixedRate(new ExpiredSessionReaperRunnable(), initialDelay, 12, TimeUnit.HOURS);
    }

    /**
     * Shuts down the expired session reaper.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * ThreadFactory for creating the daemon reaper thread.
     */
    private final class ExpiredSessionReaperThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("dynamo-session-manager-expired-sesion-reaper");
            return thread;
        }
    }

    /**
     * Runnable that is invoked periodically to scan for expired sessions.
     */
    private class ExpiredSessionReaperRunnable implements Runnable {
        @Override
        public void run() {
            reapExpiredSessions();
        }
    }

    /**
     * Scans the session table for expired sessions and deletes them.
     */
    private void reapExpiredSessions() {
        ScanRequest request = new ScanRequest(tableName);
        request.setSelect(Select.SPECIFIC_ATTRIBUTES);
        request.withAttributesToGet(ATTRIBUTES_TO_GET);
        
        long expiredBefore = System.currentTimeMillis() - expirationTimeInMillis;
        Condition condition = new Condition().withAttributeValueList(new AttributeValue()
            .withN(Long.toString(expiredBefore)))
            .withComparisonOperator(ComparisonOperator.LT);
        request.addScanFilterEntry(SessionTableAttributes.LAST_UPDATED_AT_ATTRIBUTE, condition);

        ScanResult scanResult = null;
        do {
            if (scanResult != null) request.setExclusiveStartKey(scanResult.getLastEvaluatedKey());

            scanResult = dynamo.scan(request);
            List<Map<String,AttributeValue>> items = scanResult.getItems();
            for (Map<String, AttributeValue> item : items) {
                if (Long.parseLong(item.get(SessionTableAttributes.LAST_UPDATED_AT_ATTRIBUTE).getN()) < expiredBefore) { // sanity check to minimize race condition
                    // delete the session
                    String sessionId = item.get(SessionTableAttributes.SESSION_ID_KEY).getS();
                    int sequence = Integer.parseInt(item.get(SessionTableAttributes.SESSION_SEQ_KEY).getN());
                    DynamoUtils.deleteItem(dynamo, tableName, sessionId, sequence);
                }
            }
        } while (scanResult.getLastEvaluatedKey() != null);
    }
}