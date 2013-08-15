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
package com.amazonaws.services.dynamodb.sessionmanager;

/**
 * Constants for DynamoDB table attributes
 */
public class SessionTableAttributes {
    public static final String SESSION_ID_KEY   = "sessionId";

    public static final String SESSION_DATA_ATTRIBUTE = "sessionData";
    public static final String LAST_UPDATED_AT_ATTRIBUTE = "lastUpdatedAt";
    public static final String CREATED_AT_ATTRIBUTE = "createdAt";
}