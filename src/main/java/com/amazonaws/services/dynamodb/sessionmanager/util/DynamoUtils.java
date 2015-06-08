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
package com.amazonaws.services.dynamodb.sessionmanager.util;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoSessionItem;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;

public class DynamoUtils {

    public static void createSessionTable(AmazonDynamoDBClient dynamo,
                                          String tableName,
                                          long readCapacityUnits,
                                          long writeCapacityUnits) {
        CreateTableRequest request = new CreateTableRequest().withTableName(tableName);

        request.withKeySchema(new KeySchemaElement().withAttributeName(DynamoSessionItem.SESSION_ID_ATTRIBUTE_NAME)
                .withKeyType(KeyType.HASH));

        GlobalSecondaryIndex gsi = new GlobalSecondaryIndex()
            .withIndexName(DynamoSessionItem.EXPIRED_INDEX_NAME)
            .withProvisionedThroughput(new ProvisionedThroughput()
                .withReadCapacityUnits(readCapacityUnits)
                .withWriteCapacityUnits(writeCapacityUnits))
                .withProjection(new Projection().withProjectionType(ProjectionType.KEYS_ONLY));
        gsi.withKeySchema(new KeySchemaElement().withAttributeName(DynamoSessionItem.EXPIRED_DATE_ATTRIBUTE_NAME).withKeyType(KeyType.HASH));
        gsi.withKeySchema(new KeySchemaElement().withAttributeName(DynamoSessionItem.EXPIRED_AT_ATTRIBUTE_NAME).withKeyType(KeyType.RANGE));

        request.withAttributeDefinitions(new AttributeDefinition().withAttributeName(
                DynamoSessionItem.SESSION_ID_ATTRIBUTE_NAME).withAttributeType(ScalarAttributeType.S));

        request.withAttributeDefinitions(new AttributeDefinition().withAttributeName(
                DynamoSessionItem.EXPIRED_DATE_ATTRIBUTE_NAME).withAttributeType(ScalarAttributeType.S));

        request.withAttributeDefinitions(new AttributeDefinition().withAttributeName(
                DynamoSessionItem.EXPIRED_AT_ATTRIBUTE_NAME).withAttributeType(ScalarAttributeType.N));

        request.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(readCapacityUnits)
                .withWriteCapacityUnits(writeCapacityUnits));

        request.withGlobalSecondaryIndexes(gsi);
        dynamo.createTable(request);
    }

    /**
     * Create a new DynamoDBMapper with table name override
     */
    public static DynamoDBMapper createDynamoMapper(AmazonDynamoDBClient dynamoDbClient, String tableName) {
        return new DynamoDBMapper(dynamoDbClient, new DynamoDBMapperConfig(new TableNameOverride(tableName)));
    }

}
