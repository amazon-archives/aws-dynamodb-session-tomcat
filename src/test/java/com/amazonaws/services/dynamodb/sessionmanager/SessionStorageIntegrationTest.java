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

import static com.amazonaws.services.dynamodb.sessionmanager.CustomAsserts.assertSessionEquals;
import static org.hamcrest.Matchers.emptyIterable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.apache.catalina.Session;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.services.dynamodb.sessionmanager.converters.SessionConverter;
import com.amazonaws.services.dynamodb.sessionmanager.converters.TestSessionFactory;

public class SessionStorageIntegrationTest extends SessionStorageIntegrationTestBase {

    private static DynamoSessionStorage sessionStorage;
    private static final TestSessionFactory SESSION_FACTORY = new TestSessionFactory();

    @Before
    public void setup() throws Exception {
        sessionStorage = createSessionStorage(SessionConverter
                .createDefaultSessionConverter(SESSION_FACTORY.getManager(), getClass().getClassLoader()));
    }

    @Test
    public void saveSession_ValidSession() {
        // First create a new session and persist it to DynamoDB
        Session session = SESSION_FACTORY.createStandardSession();
        final String sessionId = session.getId();
        sessionStorage.saveSession(session);

        // Make sure we can load the session we just saved
        Session loadedSession = sessionStorage.loadSession(sessionId);
        assertSessionEquals(session, loadedSession);

        // Now delete the session we saved and make sure it's gone
        sessionStorage.deleteSession(sessionId);
        assertNull(sessionStorage.loadSession(sessionId));
    }

    @Test
    public void listSessions_NoSessionsInTable_ReturnsEmptyIterable() {
        Iterable<Session> sessions = sessionStorage.listSessions();
        assertNotNull(sessions);
        assertThat(sessions, emptyIterable());
    }

    @Test
    public void countSessions_NoSessionsInTable_ReturnsZero() {
        assertEquals(0, sessionStorage.count());
    }

    @Test
    public void countSessions_OneSessionInTable_ReturnsOne() {
        Session session = SESSION_FACTORY.createStandardSession();
        sessionStorage.saveSession(session);
        assertEquals(1, sessionStorage.count());
        sessionStorage.deleteSession(session.getId());
    }

}
