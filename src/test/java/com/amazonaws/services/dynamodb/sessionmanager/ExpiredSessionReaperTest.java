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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.amazonaws.services.dynamodb.sessionmanager.converters.LegacyTomcatSessionConverter.LegacySession;
import com.amazonaws.services.dynamodb.sessionmanager.converters.TestSessionFactory;

public class ExpiredSessionReaperTest {

    @Test
    public void isExpired_ActiveSession_ReturnsFalse() {
        assertFalse(ExpiredSessionReaper.isExpired(createActiveSession()));
    }

    @Test
    public void isExpired_ExpiredSession_ReturnsTrue() {
        assertTrue(ExpiredSessionReaper.isExpired(createExpiredSession()));
    }

    @Test
    public void isExpired_ImmortalSession_ReturnsFalse() {
        assertFalse(ExpiredSessionReaper.isExpired(createImmortalSession()));
    }

    public static LegacySession createActiveSession() {
        LegacySession activeSession = new TestSessionFactory().withSessionId("active")
                .withLastAccessedTime(System.currentTimeMillis()).createLegacySession();
        return activeSession;
    }

    public static LegacySession createExpiredSession() {
        LegacySession expiredSession = new TestSessionFactory().withSessionId("expired").withLastAccessedTime(0)
                .createLegacySession();
        return expiredSession;
    }

    /**
     * A negative value for maxInactiveInterval means the session never expires. isExpired should
     * always return false no matter what
     */
    public static LegacySession createImmortalSession() {
        LegacySession immortalSession = new TestSessionFactory().withSessionId("immortal").withMaxInactiveInterval(-1)
                .withLastAccessedTime(0).createLegacySession();
        return immortalSession;
    }
}
