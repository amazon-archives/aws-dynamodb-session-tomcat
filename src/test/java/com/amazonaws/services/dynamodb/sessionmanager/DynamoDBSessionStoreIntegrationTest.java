/*
 * Copyright 2011-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import com.amazonaws.services.dynamodb.sessionmanager.converters.SessionConversionException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DynamoDBSessionStoreIntegrationTest {

    private static final String SESSION_ID = "1234";

    @Mock
    private DynamoSessionStorage storage;

    @Test
    public void loadCorruptSession_DeletesSessionWhenDeleteCorruptSessionsEnabled() throws
                                                                                    Exception {
        stubLoadCorruptSession();
        final DynamoDBSessionStore sessionStore = new DynamoDBSessionStore(storage, "tableName", true);
        assertNull(sessionStore.load(SESSION_ID));
        verify(storage, times(1)).deleteSession(SESSION_ID);
    }

    @Test
    public void loadCorruptSession_DoesNotDeletesSessionWhenDeleteCorruptSessionsDisabled() throws
                                                                                            Exception {
        stubLoadCorruptSession();
        final DynamoDBSessionStore sessionStore = new DynamoDBSessionStore(storage, "tableName", false);
        assertNull(sessionStore.load(SESSION_ID));
        verify(storage, never()).deleteSession(SESSION_ID);
    }

    private void stubLoadCorruptSession() {
        when(storage.loadSession(SESSION_ID))
                .thenThrow(new SessionConversionException("Unable to convert session"));
    }
}
