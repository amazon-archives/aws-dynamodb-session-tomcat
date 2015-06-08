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

import java.nio.ByteBuffer;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = DynamoDBSessionManager.DEFAULT_TABLE_NAME)
public class DynamoSessionItem {

    public static final String SESSION_ID_ATTRIBUTE_NAME = "sessionId";
    public static final String SESSION_DATA_ATTRIBUTE_NAME = "sessionData";
    public static final String CREATED_AT_ATTRIBUTE_NAME = "createdAt";
    public static final String LAST_UPDATED_AT_ATTRIBUTE_NAME = "lastUpdatedAt";
    public static final String EXPIRED_AT_ATTRIBUTE_NAME = "expiredAt";
    public static final String EXPIRED_DATE_ATTRIBUTE_NAME = "expiredDate";
    public static final String EXPIRED_INDEX_NAME = "expired";

    private String sessionId;
    private ByteBuffer sessionData;

    // Legacy item attributes
    private long lastUpdatedTime;
    private long createdTime;

    private long expiredAt;
    private String expiredDate;

    public DynamoSessionItem() {
    }

    public DynamoSessionItem(String id) {
        this.sessionId = id;
    }

    @DynamoDBHashKey(attributeName = SESSION_ID_ATTRIBUTE_NAME)
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @DynamoDBAttribute(attributeName = SESSION_DATA_ATTRIBUTE_NAME)
    public ByteBuffer getSessionData() {
        return sessionData;
    }

    public void setSessionData(ByteBuffer sessionData) {
        this.sessionData = sessionData;
    }

    @DynamoDBAttribute(attributeName = LAST_UPDATED_AT_ATTRIBUTE_NAME)
    public long getLastUpdatedTime() {
        return lastUpdatedTime;
    }

    public void setLastUpdatedTime(long lastUpdatedTime) {
        this.lastUpdatedTime = lastUpdatedTime;
    }

    @DynamoDBAttribute(attributeName = CREATED_AT_ATTRIBUTE_NAME)
    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = EXPIRED_INDEX_NAME, attributeName = EXPIRED_AT_ATTRIBUTE_NAME)
    public long getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(long expiredAt) {
        this.expiredAt = expiredAt;
    }

    @DynamoDBIndexHashKey(globalSecondaryIndexName = EXPIRED_INDEX_NAME, attributeName = EXPIRED_DATE_ATTRIBUTE_NAME)
    public String getExpiredDate() {
        return expiredDate;
    }

    public void setExpiredDate(String expiredDate) {
        this.expiredDate = expiredDate;
    }

}
