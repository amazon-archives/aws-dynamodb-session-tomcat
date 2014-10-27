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
        Map<String, AttributeValue> item = DynamoUtils.loadItemBySessionId(dynamo, sessionTableName, id);
        if (item == null || !item.containsKey(SessionTableAttributes.SESSION_ID_KEY) || !item.containsKey(SessionTableAttributes.SESSION_DATA_ATTRIBUTE)) {
            DynamoDBSessionManager.debug("Unable to load session attributes for session " + id);
            return null;
        }


        Session session = getManager().createSession(id);
        session.setCreationTime(Long.parseLong(item.get(SessionTableAttributes.CREATED_AT_ATTRIBUTE).getN()));


        ByteBuffer byteBuffer = item.get(SessionTableAttributes.SESSION_DATA_ATTRIBUTE).getB();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(byteBuffer.array());

        Object readObject;
        CustomObjectInputStream objectInputStream = null;
        try {
            Container webapp = getManager().getContainer();
            objectInputStream = new CustomObjectInputStream(inputStream, webapp.getLoader().getClassLoader());

            readObject = objectInputStream.readObject();
        } finally {
            try { objectInputStream.close(); } catch (Exception e) {}
        }

        if (readObject instanceof Map<?, ?>) {
            Map<String, Object> sessionAttributeMap = (Map<String, Object>)readObject;

            for (String s : sessionAttributeMap.keySet()) {
                ((StandardSession)session).setAttribute(s, sessionAttributeMap.get(s));
            }
        } else {
            throw new RuntimeException("Error: Unable to unmarshall session attributes from DynamoDB store");
        }


        keys.add(id);
        manager.add(session);

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
}