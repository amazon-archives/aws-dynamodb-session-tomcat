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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;
import com.amazonaws.util.IOUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class DefaultDynamoSessionItemConverter implements DynamoSessionItemConverter {
    private static final Log logger = LogFactory.getLog(DefaultDynamoSessionItemConverter.class);

    @Override
    public DynamoSessionItem toSessionItem(Session session) {
        DynamoSessionItem sessionItem = null;

        ObjectOutputStream oos = null;
        try {
            ByteArrayOutputStream fos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(fos);
            ((StandardSession) session).writeObjectData(oos);
            oos.close();

            sessionItem = new DynamoSessionItem(session.getIdInternal());
            sessionItem.setSessionData(ByteBuffer.wrap(fos.toByteArray()));

            Long ttl = getCurrentEpochTime() + session.getMaxInactiveInterval();
            if (logger.isDebugEnabled()) {
                logger.debug("TTL set to '" + ttl + "' (epoch time).");
            }
            sessionItem.setTtl(ttl);
        } catch (Exception e) {
            IOUtils.closeQuietly(oos, null);
            throw new SessionConversionException("Unable to convert Tomcat Session into Dynamo storage representation",
                    e);
        }

        return sessionItem;
    }

    protected long getCurrentEpochTime() {
        return TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }
}
