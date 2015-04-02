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

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;

import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.util.CustomObjectInputStream;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;
import com.amazonaws.services.dynamodb.sessionmanager.util.ValidatorUtils;
import com.amazonaws.util.IOUtils;

public class DefaultTomcatSessionConverter implements TomcatSessionConverter {

    private final ClassLoader classLoader;
    private final Manager manager;

    public DefaultTomcatSessionConverter(Manager manager, ClassLoader classLoader) {
        ValidatorUtils.nonNull(manager, "Manager");
        ValidatorUtils.nonNull(classLoader, "ClassLoader");
        this.classLoader = classLoader;
        this.manager = manager;
    }

    @Override
    public Session toSession(DynamoSessionItem sessionItem) {
        ObjectInputStream ois = null;
        try {
            ByteArrayInputStream fis = new ByteArrayInputStream(sessionItem.getSessionData().array());
            ois = new CustomObjectInputStream(fis, classLoader);

            StandardSession session = new StandardSession(manager);
            session.readObjectData(ois);
            return session;
        } catch (Exception e) {
            throw new SessionConversionException("Unable to convert Dynamo storage representation to a Tomcat Session",
                    e);
        } finally {
            IOUtils.closeQuietly(ois, null);
        }
    }

}
