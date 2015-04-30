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

import java.util.Arrays;
import java.util.List;

import org.apache.catalina.Session;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;

public class TomcatSessionConverterChain implements TomcatSessionConverter {

    private static final Log logger = LogFactory.getLog(TomcatSessionConverterChain.class);

    private final List<TomcatSessionConverter> tomcatSessionCoverters;

    private TomcatSessionConverterChain(List<TomcatSessionConverter> tomcatSessionConverters) {
        this.tomcatSessionCoverters = tomcatSessionConverters;
    }

    @Override
    public Session toSession(DynamoSessionItem sessionItem) {
        for (TomcatSessionConverter converter : tomcatSessionCoverters) {
            try {
                return converter.toSession(sessionItem);
            } catch (SessionConversionException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Could not convert session with " + converter.getClass().getSimpleName(), e);
                }
                // Try next converter in chain
            }
        }
        throw new SessionConversionException(
                "Unable to convert Dynamo storage representation to a Tomcat Session with any converter provided. "
                        + "Turn on debug logging to get more detailed information about the actual exception encountered");
    }

    public static TomcatSessionConverter wrap(TomcatSessionConverter... converters) {
        return new TomcatSessionConverterChain(Arrays.asList(asArray(converters)));
    }

    private static TomcatSessionConverter[] asArray(TomcatSessionConverter[] converters) {
        return converters;
    }
}
