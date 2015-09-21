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

import org.apache.catalina.Manager;
import org.apache.catalina.Session;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;
import com.amazonaws.services.dynamodb.sessionmanager.util.ValidatorUtils;

public final class SessionConverter implements TomcatSessionConverter, DynamoSessionItemConverter {

    private final TomcatSessionConverter fromDynamo;
    private final DynamoSessionItemConverter toDynamo;

    public SessionConverter(TomcatSessionConverter fromDynamo, DynamoSessionItemConverter toDynamo) {
        ValidatorUtils.nonNull(fromDynamo, "TomcatSessionConverter");
        ValidatorUtils.nonNull(toDynamo, "DynamoSessionItemConverter");
        this.fromDynamo = fromDynamo;
        this.toDynamo = toDynamo;
    }

    @Override
    public DynamoSessionItem toSessionItem(Session session) {
        return toDynamo.toSessionItem(session);
    }

    @Override
    public Session toSession(DynamoSessionItem dynamoSessionItem) {
        return fromDynamo.toSession(dynamoSessionItem);
    }

    /**
     * Factory method to create a SessionConverter with the default implementation of
     * TomcatSessionConverter and DynamoSessionConverter
     */
    public static SessionConverter createDefaultSessionConverter(Manager manager, ClassLoader classLoader) {
        return new SessionConverter(new DefaultTomcatSessionConverter(manager, classLoader),
                new DefaultDynamoSessionItemConverter());
    }

}