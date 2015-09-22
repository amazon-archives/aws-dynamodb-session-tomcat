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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.apache.catalina.Session;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.services.dynamodb.sessionmanager.converters.SessionConverter;
import com.amazonaws.services.dynamodb.sessionmanager.converters.TestSessionFactory;
import com.amazonaws.services.dynamodb.sessionmanager.converters.TestSessionFactory.TestStandardSession;

public class ExpiredSessionReaperIntegrationTest extends SessionStorageIntegrationTestBase {

    private DynamoSessionStorage sessionStorage;

    @Before
    public void setup() {
        sessionStorage = createSessionStorage(SessionConverter
                .createDefaultSessionConverter(new TestSessionFactory().getManager(), getClass().getClassLoader()));
    }

    /**
     * Integration test for ExpiredSessionReaper. Makes sure expired sessions are deleted from the
     * DynamoDB table and non-expired sessions are not deleted
     */
    @Test
    public void testSessionReaping() {
        TestStandardSession activeSession = ExpiredSessionReaperTest.createActiveSession();
        TestStandardSession expiredSession = ExpiredSessionReaperTest.createExpiredSession();
        TestStandardSession immortalSession = ExpiredSessionReaperTest.createImmortalSession();
        saveSessions(sessionStorage, activeSession, expiredSession, immortalSession);

        new ExpiredSessionReaper(sessionStorage).run();

        assertNotNull(sessionStorage.loadSession(activeSession.getId()));
        assertNull(sessionStorage.loadSession(expiredSession.getId()));
        assertNotNull(sessionStorage.loadSession(immortalSession.getId()));
    }

    private void saveSessions(DynamoSessionStorage sessionStorage, Session... sessions) {
        for (Session session : sessions) {
            sessionStorage.saveSession(session);
        }
    }

}
