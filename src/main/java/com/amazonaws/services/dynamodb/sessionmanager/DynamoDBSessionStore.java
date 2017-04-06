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

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.catalina.Session;
import org.apache.catalina.session.StoreBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.amazonaws.services.dynamodb.sessionmanager.converters.SessionConversionException;
import com.amazonaws.services.dynamodb.sessionmanager.util.ValidatorUtils;

/**
 * Session store implementation that loads and stores HTTP sessions from Amazon DynamoDB.
 */
public class DynamoDBSessionStore extends StoreBase {
    private static final Log logger = LogFactory.getLog(DynamoDBSessionStore.class);

    private final Set<String> sessionIds = Collections.synchronizedSet(new HashSet<String>());
    private final DynamoSessionStorage sessionStorage;

    private final boolean deleteCorruptSessions;

    private String name;

    public DynamoDBSessionStore(final DynamoSessionStorage sessionStorage, final String name,
            final boolean deleteCorruptSessions) {
        ValidatorUtils.nonNull(sessionStorage, "SessionStorage");

        this.sessionStorage = sessionStorage;
        this.name = name;

        this.deleteCorruptSessions = deleteCorruptSessions;
    }

    @Override
    public String getStoreName() {
        return name;
    }

    @Override
    public void clear() throws IOException {
        synchronized (sessionIds) {
            final Set<String> sessionsToDelete = new HashSet<String>(sessionIds);
            new Thread("dynamodb-session-manager-clear") {
                @Override
                public void run() {
                    for (String sessionId : sessionsToDelete) {
                        sessionStorage.deleteSession(sessionId);
                    }
                }
            }.start();
            sessionIds.clear();
        }
    }

    @Override
    public int getSize() throws IOException {
        return sessionStorage.count();
    }

    @Override
    public String[] keys() throws IOException {
        return sessionIds.toArray(new String[0]);
    }

    @Override
    public Session load(String id) throws ClassNotFoundException, IOException {
        Session session = tryLoadSession(id);
        if (session == null) {
            logger.warn("Unable to load session with id " + id);
            return null;
        }

        sessionIds.add(id);
        return session;
    }

    @Override
    public void save(Session session) throws IOException {
        sessionStorage.saveSession(session);
        sessionIds.add(session.getId());
    }

    @Override
    public void remove(String id) throws IOException {
        sessionStorage.deleteSession(id);
        sessionIds.remove(id);
    }

    private Session tryLoadSession(String id) {
        try {
            return sessionStorage.loadSession(id);
        } catch (SessionConversionException e) {
            if (deleteCorruptSessions) {
                deleteCorruptSession(id, e);
            }
        }
        return null;
    }

    /**
     * Delete corrupt session from Dynamo if configured to do so.
     *
     * @param id ID of session to delete
     * @param e  Exception that caused the session to fail to deserialize
     */
    private void deleteCorruptSession(String id, SessionConversionException e) {
        logger.warn("Unable to load session with id " + id + ". Deleting from session store", e);
        sessionStorage.deleteSession(id);
    }
}
