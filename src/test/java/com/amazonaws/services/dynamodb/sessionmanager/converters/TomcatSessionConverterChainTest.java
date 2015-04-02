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

import static org.junit.Assert.assertEquals;

import org.apache.catalina.Session;
import org.junit.Test;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;

public class TomcatSessionConverterChainTest {

    private static final Session SESSION = new TestSessionFactory().createStandardSession();

    private class FaultyTomcatSessionConverter implements TomcatSessionConverter {
        @Override
        public Session toSession(DynamoSessionItem sessionItem) {
            throw new SessionConversionException("Unable to convert");
        }
    }

    private class WorkingTomcatSessionConverter implements TomcatSessionConverter {
        @Override
        public Session toSession(DynamoSessionItem sessionItem) {
            return SESSION;
        }
    }

    @Test
    public void toSession_FaultyConverterFirst_FallsBackToWorkingConverter() throws Exception {
        TomcatSessionConverter converterChain = TomcatSessionConverterChain.wrap(new FaultyTomcatSessionConverter(),
                new WorkingTomcatSessionConverter());
        assertEquals(SESSION, converterChain.toSession(null));
    }

    @Test
    public void toSession_WorkingConverterFirst_UsesWorkingConverter() throws Exception {
        TomcatSessionConverter converterChain = TomcatSessionConverterChain.wrap(new WorkingTomcatSessionConverter(),
                new FaultyTomcatSessionConverter());
        assertEquals(SESSION, converterChain.toSession(null));
    }

    @Test(expected = SessionConversionException.class)
    public void toSession_AllConvertersFail_ThrowsDynamoSessionConversionException() {
        TomcatSessionConverter converterChain = TomcatSessionConverterChain.wrap(new FaultyTomcatSessionConverter(),
                new FaultyTomcatSessionConverter());
        converterChain.toSession(null);
    }

    @Test(expected = SessionConversionException.class)
    public void toSession_NoConverters_ThrowsDynamoSessionConversionException() {
        TomcatSessionConverter converterChain = TomcatSessionConverterChain.wrap();
        converterChain.toSession(null);
    }

}
