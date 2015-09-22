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

import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests a SessionConverter with Default implementations of TomcatSessionConverter and
 * DynamoSessionConverter
 */
public class DefaultSessionConverterTest {

    private static final TestSessionFactory SESSION_TEMPLATE = new TestSessionFactory();

    private SessionConverter sessionConverter;
    private StandardSession session;

    @Before
    public void setup() {
        sessionConverter = SessionConverter.createDefaultSessionConverter(SESSION_TEMPLATE.getManager(),
                getClass().getClassLoader());
        session = SESSION_TEMPLATE.createStandardSession();
    }

    @Test
    public void roundTrip_ReturnsSameSession() throws Exception {
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
    public void toSessionItem_NullSession_ThrowsSessionConversionException() {
        assertNull(sessionConverter.toSessionItem(null));
    }

    @Test(expected = SessionConversionException.class)
    public void toSession_NullSessionItem_ThrowsSessionConversionException() {
        assertNull(sessionConverter.toSession(null));
    }

    @Test(expected = SessionConversionException.class)
    public void toSessionItem_NullManager_ThrowsSessionConversionException() {
        session.setManager(null);
        sessionConverter.toSessionItem(session);
    }

}
