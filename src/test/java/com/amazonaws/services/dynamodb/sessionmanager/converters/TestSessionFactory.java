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

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;

import com.amazonaws.services.dynamodb.sessionmanager.CustomSessionClass;
import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;

/**
 * Utility class to create new {@link Session} and {@link DynamoSessionItem} object for tests. Has
 * suitable defaults for fields that can be changed for individual test classes or cases
 */
public class TestSessionFactory {

    // Initialize fields with some defaults
    private String sessionId = "1234";
    private int creationTime = 1234;
    private long lastAccessedTime = creationTime;
    private int maxInactiveInterval = 30;
    private Manager manager = getDefaultManager();
    private Map<String, Object> sessionAttributes = getDefaultSessionAttributes();

    public String getSessionId() {
        return sessionId;
    }

    public int getCreationTime() {
        return creationTime;
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public Map<String, Object> getSessionAttributes() {
        return sessionAttributes;
    }

    public Manager getManager() {
        return manager;
    }

    public TestSessionFactory withSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public TestSessionFactory withCreationTime(int creationTime) {
        this.creationTime = creationTime;
        return this;
    }

    public TestSessionFactory withLastAccessedTime(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
        return this;
    }

    public TestSessionFactory withMaxInactiveInterval(int maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
        return this;
    }

    public TestSessionFactory withSessionAttributes(Map<String, Object> sessionAttributes) {
        this.sessionAttributes = sessionAttributes;
        return this;
    }

    public TestSessionFactory withManager(Manager manager) {
        this.manager = manager;
        return this;
    }

    public final StandardSession createStandardSession() {
        TestStandardSession session = new TestStandardSession(null);
        session.setValid(true);
        session.setId(getSessionId(), false);
        session.setCreationTime(getCreationTime());
        session.setMaxInactiveInterval(maxInactiveInterval);
        session.setLastAccessedTime(lastAccessedTime);

        Map<String, Object> sessionData = getSessionAttributes();
        if (sessionData != null) {
            for (Entry<String, Object> attr : sessionData.entrySet()) {
                session.setAttribute(attr.getKey(), attr.getValue(), false);
            }
        }
        session.setManager(getManager());
        return session;
    }

    public final TestStandardSession createTestStandardSession() {
        return (TestStandardSession) createStandardSession();
    }

    private static Map<String, Object> getDefaultSessionAttributes() {
        Map<String, Object> sessionData = new HashMap<String, Object>();
        sessionData.put("someAttribute", new CustomSessionClass("customData"));
        return sessionData;
    }

    private static Manager getDefaultManager() {
        Manager mockManager = mock(Manager.class, RETURNS_DEEP_STUBS);
        when(mockManager.getContext().getLogger().isDebugEnabled()).thenReturn(false);
        return mockManager;
    }

    /**
     * Subclassed standard session to allow setting lastAccessedTime through means other than
     * readObject
     */
    public static class TestStandardSession extends StandardSession {

        private static final long serialVersionUID = 9163946735192227235L;

        public TestStandardSession(Manager manager) {
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
