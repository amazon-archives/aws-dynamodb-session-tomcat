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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.catalina.Container;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.StoreBase;
import org.apache.catalina.util.CustomObjectInputStream;

import com.amazonaws.services.dynamodb.sessionmanager.util.DynamoUtils;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.TableDescription;

/**
 * Session store implementation that loads and stores HTTP sessions from Amazon
 * DynamoDB.
 */
public class DynamoDBSessionStore extends StoreBase {

    private static final String name = "AmazonDynamoDBSessionStore";
    private static final String info = name + "/1.0";

    private AmazonDynamoDBClient dynamo;
    private String sessionTableName;

    private Set<String> keys = Collections.synchronizedSet(new HashSet<String>());

    
    @Override
    public String getInfo() {
        return info;
    }

    @Override
    public String getStoreName() {
        return name;
    }

    public void setDynamoClient(AmazonDynamoDBClient dynamo) {
        this.dynamo = dynamo;
    }

    public void setSessionTableName(String tableName) {
        this.sessionTableName = tableName;
    }


    @Override
    public void clear() throws IOException {
        final Set<String> keysCopy = new HashSet<String>();
        keysCopy.addAll(keys);

        new Thread("dynamodb-session-manager-clear") {
            @Override
            public void run() {
                for (String sessionId : keysCopy) {
                    DynamoUtils.deleteSession(dynamo, sessionTableName, sessionId);
                }
            }
        }.start();

        keys.clear();
    }

    @Override
    public int getSize() throws IOException {
        // The item count from describeTable is updated every ~6 hours
        TableDescription table = dynamo.describeTable(new DescribeTableRequest().withTableName(sessionTableName)).getTable();
        long itemCount = table.getItemCount();

        return (int)itemCount;
    }

    @Override
    public String[] keys() throws IOException {
        return keys.toArray(new String[0]);
    }

    @Override
    public Session load(String id) throws ClassNotFoundException, IOException {
        DynamoDBSessionManager manager = (DynamoDBSessionManager) getManager();
        Session session = DynamoUtils.loadSession(dynamo, sessionTableName, id, manager);
        if (session != null) {
          keys.add(id);
          manager.add(session);
        }
        return session;
    }

    @Override
    public void save(Session session) throws IOException {
        DynamoUtils.storeSession(dynamo, sessionTableName, session);
        keys.add(session.getId());
    }

    @Override
    public void remove(String id) throws IOException {
        DynamoUtils.deleteSession(dynamo, sessionTableName, id);
        keys.remove(id);
    }

    /**
     * Called by the Tomcat background reaper thread to check if Sessions
     * saved in our store are subject to being expired. We override here
     * because we only care about loaded sessions in this call, and let the
     * DynamoDB ExpiredSessionReaper clean up the DB periodically.
     * Note that if MinIdleSwap and MaxIdleSwap are set such that swapout 
     * occurs well ahead of expiration, we never expire any in-memory sessions.
     * However, if a swapped out session has actually expired, but has not yet
     * been removed from the DB by ExpiredSessionReaper, then it will be 
     * recognized as not valid upon load, so our overload has no detrimental effect.
     * 
     * Most importantly, this prevents constant reloading of sessions from DB for expiry check, 
     * resulting in increased memory utilization, unnecessary DB activity, and occasional 
     * loss of data, since a recently modified session (less than MaxIdleBackup ago)
     * would NOT have been persisted, and we previously would have replaced it with an
     * older object from the DB.
     *
     */
    @Override
    public void processExpires() {
      String[] keys = null;

      if(!getState().isAvailable()) {
        return;
      }

      try {
        keys = keys();
      } catch (IOException e) {
        manager.getContainer().getLogger().error("Error getting keys", e);
        return;
      }
      if (manager.getContainer().getLogger().isDebugEnabled()) {
        manager.getContainer().getLogger().debug(getStoreName()+ ": processExpires check number of " + keys.length + " sessions" );
      }

      for (int i = 0; i < keys.length; i++) {
        String key = keys[i];
        // We use a new method added to DynamoDBSessionManager to determine if session is loaded and valid
        // because only the Manager can make this determination without calling findSession and isValid, which:
        // a) Artificially mark the session as accessed, preventing appropriate swap processing.
        // b) Attempting to load the session into memory if it is not already there, which we want to avoid.
        ((DynamoDBSessionManager) manager).isLoadedAndValid(key); // this expires if in memory and not valid
        // We let the DynamoDB ExpiredSessionReaper clean up the DB eventually, 
        // but if an expired session is swapped in before that cleanup, it will be declared isInvalid upon load anyway.
      }
      
    }

}