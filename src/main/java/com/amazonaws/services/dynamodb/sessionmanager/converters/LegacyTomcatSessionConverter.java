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
package com.amazonaws.services.dynamodb.sessionmanager.converters;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.util.CustomObjectInputStream;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;
import com.amazonaws.services.dynamodb.sessionmanager.util.ValidatorUtils;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.IOUtils;

public class LegacyTomcatSessionConverter implements TomcatSessionConverter {

    private final Manager manager;
    private final ClassLoader classLoader;
    private final int maxInactiveInterval;

    public LegacyTomcatSessionConverter(Manager manager, ClassLoader classLoader, int maxInactiveInterval) {
        ValidatorUtils.nonNull(manager, "Manager");
        ValidatorUtils.nonNull(classLoader, "ClassLoader");
        this.manager = manager;
        this.classLoader = classLoader;
        this.maxInactiveInterval = maxInactiveInterval;
    }

    @Override
    public Session toSession(DynamoSessionItem sessionItem) {
        try {
            LegacySession session = new LegacySession(null);
            session.setValid(true);
            session.setId(sessionItem.getSessionId(), false);
            session.setCreationTime(sessionItem.getCreatedTime());
            session.setLastAccessedTime(sessionItem.getLastUpdatedTime());
            session.setMaxInactiveInterval(maxInactiveInterval);
            session.setSessionAttributes(unmarshallSessionData(sessionItem));
            session.setManager(manager);
            return session;
        } catch (Exception e) {
            throw new SessionConversionException("Unable to convert Dynamo storage representation to a Tomcat Session",
                    e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unmarshallSessionData(DynamoSessionItem sessionItem) throws IOException,
            ClassNotFoundException {
        ByteBuffer rawSessionData = sessionItem.getSessionData();

        Object marshalledSessionData;
        ObjectInputStream objectInputStream = null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(BinaryUtils.copyAllBytesFrom(rawSessionData));
            objectInputStream = new CustomObjectInputStream(inputStream, classLoader);
            marshalledSessionData = objectInputStream.readObject();
        } finally {
            IOUtils.closeQuietly(objectInputStream, null);
        }
        if (!(marshalledSessionData instanceof Map<?, ?>)) {
            throw new SessionConversionException("Unable to unmarshall session attributes from DynamoDB store");
        }
        return (Map<String, Object>) marshalledSessionData;
    }

    /**
     * Subclassed standard session to allow setting lastAccessedTime through means other than
     * readObject
     */
    public static class LegacySession extends StandardSession {

        private static final long serialVersionUID = 9163946735192227235L;

        public LegacySession(Manager manager) {
            super(manager);
        }

        public void setLastAccessedTime(long lastAccessedTime) {
            this.lastAccessedTime = lastAccessedTime;
            this.thisAccessedTime = lastAccessedTime;
        }

        public void setSessionAttributes(Map<String, Object> attributes) {
            for (Entry<String, Object> attribute : attributes.entrySet()) {
                this.setAttribute(attribute.getKey(), attribute.getValue(), false);
            }
        }

    }
}
