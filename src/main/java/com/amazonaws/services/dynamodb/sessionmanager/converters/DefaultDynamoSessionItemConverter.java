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
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;
import com.amazonaws.util.IOUtils;

public class DefaultDynamoSessionItemConverter implements DynamoSessionItemConverter {

    @Override
    public DynamoSessionItem toSessionItem(Session session) {
        ObjectOutputStream oos = null;
        try {
            ByteArrayOutputStream fos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(fos);
            ((StandardSession) session).writeObjectData(oos);
            oos.close();
            DynamoSessionItem sessionItem = new DynamoSessionItem(session.getIdInternal());
            sessionItem.setCreatedTime(session.getCreationTimeInternal());
            sessionItem.setLastUpdatedTime(session.getLastAccessedTimeInternal());
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(session.getLastAccessedTimeInternal());
            calendar.add(Calendar.SECOND, session.getMaxInactiveInterval());
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            String date = format.format(calendar.getTime());
            sessionItem.setExpiredDate(date);
            sessionItem.setExpiredAt(calendar.getTimeInMillis());
            sessionItem.setSessionData(ByteBuffer.wrap(fos.toByteArray()));
            return sessionItem;
        } catch (Exception e) {
            IOUtils.closeQuietly(oos, null);
            throw new SessionConversionException("Unable to convert Tomcat Session into Dynamo storage representation",
                    e);
        }
    }
}
