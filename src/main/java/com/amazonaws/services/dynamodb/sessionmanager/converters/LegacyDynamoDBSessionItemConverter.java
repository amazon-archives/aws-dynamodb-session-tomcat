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
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.catalina.Session;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;

public class LegacyDynamoDBSessionItemConverter implements DynamoSessionItemConverter {

    @Override
    public DynamoSessionItem toSessionItem(Session session) {
        try {
            DynamoSessionItem sessionItem = new DynamoSessionItem(session.getIdInternal());
            sessionItem.setCreatedTime(session.getCreationTimeInternal());
            sessionItem.setLastUpdatedTime(session.getLastAccessedTimeInternal());
            sessionItem.setSessionData(sessionDataToByteBuffer(session));
            return sessionItem;
        } catch (Exception e) {
            throw new SessionConversionException("Unable to convert Tomcat Session into Dynamo storage representation",
                    e);
        }
    }

    private static ByteBuffer sessionDataToByteBuffer(Session session) throws IOException {
        Map<String, Object> getterReturnResult = sessionDataToMap(session);
        return objectToByteBuffer(getterReturnResult);
    }

    private static Map<String, Object> sessionDataToMap(Session session) {
        HttpSession httpSession = session.getSession();
        Map<String, Object> sessionAttributes = new HashMap<String, Object>();
        Enumeration<String> attributeNames = httpSession.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String attributeName = attributeNames.nextElement();
            Object attributeValue = httpSession.getAttribute(attributeName);
            sessionAttributes.put(attributeName, attributeValue);
        }
        return sessionAttributes;
    }

    static ByteBuffer objectToByteBuffer(Object object) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(object);
        objectOutputStream.close();
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return ByteBuffer.wrap(byteArray);
    }
}
