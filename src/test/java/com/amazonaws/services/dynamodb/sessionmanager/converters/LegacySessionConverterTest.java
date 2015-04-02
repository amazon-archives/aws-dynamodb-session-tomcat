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

import static com.amazonaws.services.dynamodb.sessionmanager.CustomAsserts.assertSessionEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.services.dynamodb.sessionmanager.CustomSessionClass;
import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;

/**
 * Tests a SessionConverter with Legacy implementations of TomcatSessionConverter and
 * DynamoSessionConverter
 */
public class LegacySessionConverterTest {

    private static final int MAX_INACTIVE_INTERVAL = 60;
    private static final TestSessionFactory SESSION_TEMPLATE = new TestSessionFactory();

    private SessionConverter sessionConverter;

    @Before
    public void setup() {
        sessionConverter = SessionConverter.createLegacySessionConverter(SESSION_TEMPLATE.getManager(), getClass()
                .getClassLoader(), MAX_INACTIVE_INTERVAL);
    }

    /**
     * Creates a StandardSession, converts it to a SessionItem and then back again to a
     * StandardSession and assert we have the same thing
     */
    @Test
    public void roundTrip_ReturnsSameSession() throws Exception {
        StandardSession session = SESSION_TEMPLATE.createStandardSession();
        Session roundTripSession = sessionConverter.toSession(sessionConverter.toSessionItem(session));
        assertSessionEquals(session, roundTripSession);
    }

    @Test
    public void roundTrip_NoSessionData_ReturnsSameSession() throws Exception {
        StandardSession session = new TestSessionFactory().withSessionAttributes(null).createStandardSession();
        Session roundTripSession = sessionConverter.toSession(sessionConverter.toSessionItem(session));
        assertSessionEquals(session, roundTripSession);
    }

    @Test(expected = SessionConversionException.class)
    public void toSessionItem_StandardSessionNull_ThrowsSessionConversionException() {
        assertNull(sessionConverter.toSessionItem(null));
    }

    @Test(expected = SessionConversionException.class)
    public void toSession_SessionItemNull_ThrowsSessionConversionException() {
        assertNull(sessionConverter.toSession(null));
    }

    @Test(expected = SessionConversionException.class)
    public void toHttpSession_JunkSessionData_ThrowsSessionConversionException() throws IOException {
        DynamoSessionItem sessionItem = SESSION_TEMPLATE.createLegacySessionItem();
        sessionItem.setSessionData(ByteBuffer.wrap(new byte[] { 1, 2, 3, 4 }));
        sessionConverter.toSession(sessionItem);
    }

    /**
     * Session Data is expected to be a Map<String, Object>. This tests a session item whose session
     * data is just a pojo throws an exception
     */
    @Test(expected = SessionConversionException.class)
    public void toHttpSession_SessionDataInvalidClass_ThrowsSessionConversionException() throws Exception {
        DynamoSessionItem sessionItem = SESSION_TEMPLATE.createLegacySessionItem();
        sessionItem.setSessionData(LegacyDynamoDBSessionItemConverter
                .objectToByteBuffer(new CustomSessionClass("data")));
        sessionConverter.toSession(sessionItem);
    }

}
