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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpSession;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import org.apache.catalina.Container;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.util.CustomObjectInputStream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.dynamodb.sessionmanager.DynamoDBSessionManager;
import com.amazonaws.services.dynamodb.sessionmanager.SessionTableAttributes;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.*;

/**
 * Utilities for working with Amazon DynamoDB for session management.
 */
public class DynamoUtils {

    private static boolean consistentRead = false;
    private static int sessionIdLength = 0;
    private static int minSizeForCompression = 4097; // Default is anything bigger than single read unit
    private static final int DYNAMODB_ITEM_SIZE_LIMIT = 65536;
    private static LZ4Factory lz4Factory = LZ4Factory.fastestInstance();
    private static byte[] placeHolder = new byte[8];

    public static void setConsistentRead(boolean setting) {
	consistentRead = setting;
    }

    public static void setSessionIdLength(int length) {
	// SessionId actually contains two characters representing hexadecimal "digits" for each 1 of configured length
	sessionIdLength = length * 2;
    }

    public static void setMinSizeForCompression(int size) {
	minSizeForCompression = size;
    }

    public static Session loadSession(AmazonDynamoDB dynamo, String tableName, String sessionId, DynamoDBSessionManager manager) {
	// TODO:  Rework to use query with KeyConditions: SESSION_ID_KEY EQ sessionId.substring(0, sessionIdLength) to get all related items
	// process all needed items, then delete any items beyond the number needed to persist current size of session.
	// use ScanIndexForward default of true, so we get the items in sequence
	Map<String, AttributeValue> map = newAttributeValueMap();
	map.put(SessionTableAttributes.SESSION_ID_KEY, new AttributeValue(sessionId.substring(0, sessionIdLength)));
	int sequence = 1;
	String value = Integer.toString(sequence++);
	map.put(SessionTableAttributes.SESSION_SEQ_KEY, new AttributeValue().withN(value));
	GetItemRequest request = new GetItemRequest(tableName, map);
	if (consistentRead) request.setConsistentRead(true);
	addClientMarker(request);

	try {
	    GetItemResult result = dynamo.getItem(request);
	    Map<String, AttributeValue> item = result.getItem();
	    if (item == null || !item.containsKey(SessionTableAttributes.SESSION_ID_KEY) || 
		    !item.containsKey(SessionTableAttributes.SESSION_SEQ_KEY) || 
		    !item.containsKey(SessionTableAttributes.SESSION_DATA_ATTRIBUTE)) {
		DynamoDBSessionManager.warn("Unable to load session attributes for session " + sessionId);
		return null;
	    }


	    Session session = manager.createSession(sessionId);
	    // This also sets lastAccessedTime = creationTime, so we need to set to SessionTableAttributes.LAST_UPDATED_AT_ATTRIBUTE 
	    // instead of CREATED_AT_ATTRIBUTE, to prevent premature expiration of session on load
	    session.setCreationTime(Long.parseLong(item.get(SessionTableAttributes.LAST_UPDATED_AT_ATTRIBUTE).getN()));


	    ByteBuffer byteBuffer = item.get(SessionTableAttributes.SESSION_DATA_ATTRIBUTE).getB();
	    ByteBuffer buffer = byteBuffer.asReadOnlyBuffer();
	    buffer.limit(placeHolder.length);
	    buffer.order(ByteOrder.BIG_ENDIAN); // just need to choose one and use it on both putting and getting.
	    int decompressedLength = buffer.getInt();
	    int compressedLength = buffer.getInt();

	    if (byteBuffer.remaining() < compressedLength + placeHolder.length) { // only part is in this item, so need to get more...
		buffer = ByteBuffer.allocate(compressedLength + placeHolder.length);
		buffer.put(byteBuffer);
		while(buffer.remaining() > 0) {
		    value = Integer.toString(sequence++);
		    map.put(SessionTableAttributes.SESSION_SEQ_KEY, new AttributeValue().withN(value));
		    request = new GetItemRequest(tableName, map);
		    if (consistentRead) request.setConsistentRead(true);
		    addClientMarker(request);

		    result = dynamo.getItem(request);
		    item = result.getItem();
		    if (item == null || !item.containsKey(SessionTableAttributes.SESSION_ID_KEY) || 
			    !item.containsKey(SessionTableAttributes.SESSION_SEQ_KEY) || 
			    !item.containsKey(SessionTableAttributes.SESSION_DATA_ATTRIBUTE)) {
			DynamoDBSessionManager.warn("Unable to load session attributes for session " + sessionId);
				return null;
		    }
		    byteBuffer = item.get(SessionTableAttributes.SESSION_DATA_ATTRIBUTE).getB();
		    buffer.put(byteBuffer);
		}
		byteBuffer = buffer;
	    }

	    ByteArrayInputStream inputStream;
	    if (decompressedLength != compressedLength) { // handle decompressing the data
		byte[] compressed = byteBuffer.array();
		byte[] byteArray = new byte[decompressedLength];
		LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();
		decompressor.decompress(compressed, placeHolder.length, byteArray, 0, decompressedLength);
		inputStream = new ByteArrayInputStream(byteArray);
	    } else {
		inputStream = new ByteArrayInputStream(byteBuffer.array());
		inputStream.read(new byte[placeHolder.length]); // advance the stream past the length fields
	    }

	    Object readObject;
	    CustomObjectInputStream objectInputStream = null;
	    try {
		Container webapp = manager.getContainer();
		objectInputStream = new CustomObjectInputStream(inputStream, webapp.getLoader().getClassLoader());

		readObject = objectInputStream.readObject();
	    } finally {
		try { objectInputStream.close(); } catch (Exception e) {}
	    }

	    if (readObject instanceof Map<?, ?>) {
		Map<String, Object> sessionAttributeMap = (Map<String, Object>)readObject;

		for (String s : sessionAttributeMap.keySet()) {
		    ((StandardSession)session).setAttribute(s, sessionAttributeMap.get(s));
		}
		return session;
	    } else {
		throw new RuntimeException("Error: Unable to unmarshall session attributes from DynamoDB store");
	    }

	} catch (Exception e) {
	    DynamoDBSessionManager.warn("Unable to load session " + sessionId, e);
	}

	return null;
    }

    public static void deleteSession(AmazonDynamoDB dynamo, String tableName, String sessionId) {
	// TODO:  rework to use query with KeyConditions: SESSION_ID_KEY EQ sessionId.substring(0, sessionIdLength) to get all related items
	// use ScanIndexForward default of true, so we get the items in sequence
	// set AttributesToGet to retrieve only sessionId and sequence
	// For now, will orphan supplemental items until cleanup by ExiredSessionReaper
	deleteItem(dynamo, tableName, sessionId, 1);
    }

    public static void deleteItem(AmazonDynamoDB dynamo, String tableName, String sessionId, int sequence) {
	Map<String, AttributeValue> key = newAttributeValueMap();
	key.put(SessionTableAttributes.SESSION_ID_KEY, new AttributeValue(sessionId.substring(0, sessionIdLength)));
	String value = Integer.toString(sequence);
	key.put(SessionTableAttributes.SESSION_SEQ_KEY, new AttributeValue().withN(value));

	DeleteItemRequest request = new DeleteItemRequest(tableName, key);
	addClientMarker(request);

	try {
	    dynamo.deleteItem(request);
	} catch (Exception e) {
	    DynamoDBSessionManager.warn("Unable to delete session " + sessionId + " item " + String.valueOf(sequence), e);
	}
    }

    public static void storeSession(AmazonDynamoDB dynamo, String tableName, Session session) throws IOException {
	Map<String, Object> sessionAttributes = new TreeMap<String, Object>();

	HttpSession httpSession = session.getSession();
	Enumeration<String> attributeNames = httpSession.getAttributeNames();
	while (attributeNames.hasMoreElements()) {
	    String attributeName = attributeNames.nextElement();
	    Object attributeValue = httpSession.getAttribute(attributeName);
	    sessionAttributes.put(attributeName, attributeValue);
	}

	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	byteArrayOutputStream.write(placeHolder); // write 8 bytes at beginning as placeholder for lengths
	ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
	objectOutputStream.writeObject(sessionAttributes);
	objectOutputStream.close();

	byte[] byteArray = byteArrayOutputStream.toByteArray();
	// release stream objects so internal storage can be reclaimed, now that byte[] has been copied out.
	byteArrayOutputStream = null;
	objectOutputStream = null; 
	int decompressedLength = byteArray.length - placeHolder.length;

	Map<String, AttributeValue> attributes = newAttributeValueMap();
	attributes.put(SessionTableAttributes.SESSION_ID_KEY, new AttributeValue(session.getId().substring(0, sessionIdLength)));
	String value = Long.toString(session.getLastAccessedTimeInternal());
	attributes.put(SessionTableAttributes.LAST_UPDATED_AT_ATTRIBUTE, new AttributeValue().withN(value));
	int overhead = SessionTableAttributes.NAME_LENGTHS + sessionIdLength + value.length();

	int compressedLength = decompressedLength;
	boolean useCompress = (decompressedLength >= minSizeForCompression);

	if (useCompress) {
	    // compress data
	    LZ4Compressor compressor = lz4Factory.fastCompressor();
	    int maxCompressedLength = compressor.maxCompressedLength(decompressedLength);
	    byte[] compressed = new byte[maxCompressedLength + placeHolder.length];
	    compressedLength = compressor.compress(byteArray, placeHolder.length, decompressedLength, compressed, placeHolder.length, maxCompressedLength);

	    byteArray = compressed;
	}

	// write the uncompressed and compressed lengths into the placeholder bytes at the front of byteArray
	ByteBuffer buffer = ByteBuffer.wrap(byteArray, 0, placeHolder.length);
	buffer.order(ByteOrder.BIG_ENDIAN); // just need to choose one and use it on both putting and getting.
	buffer.putInt(decompressedLength).putInt(compressedLength);
	int sequence = 1;

	int bytesRemaining = compressedLength + placeHolder.length;
	int bufferOffset = 0;

	try {
	    while (bytesRemaining > 0) {
		value = Integer.toString(sequence++);
		attributes.put(SessionTableAttributes.SESSION_SEQ_KEY, new AttributeValue().withN(value));
		int roomAvailable = DYNAMODB_ITEM_SIZE_LIMIT - overhead - value.length();
		if (roomAvailable > bytesRemaining) roomAvailable = bytesRemaining;
		buffer = ByteBuffer.wrap(byteArray, bufferOffset, roomAvailable);
		attributes.put(SessionTableAttributes.SESSION_DATA_ATTRIBUTE, new AttributeValue().withB(buffer));

		PutItemRequest request = new PutItemRequest(tableName, attributes);
		addClientMarker(request);
		dynamo.putItem(request);

		// Update counters with amount of byteArray data written in this Item.
		bytesRemaining -= roomAvailable;
		bufferOffset += roomAvailable;
	    }
	} catch (Exception e) {
	    DynamoDBSessionManager.error("Unable to save session " + session.getId(), e);
	}
    }

    public static boolean doesTableExist(AmazonDynamoDBClient dynamo, String tableName) {
	try {
	    DescribeTableRequest request = new DescribeTableRequest().withTableName(tableName);
	    addClientMarker(request);

	    TableDescription table = dynamo.describeTable(request).getTable();
	    if (table == null) return false;
	    else return true;
	} catch (AmazonServiceException ase) {
	    if (ase.getErrorCode().equalsIgnoreCase("ResourceNotFoundException")) return false;
	    else throw ase;
	}
    }

    public static void waitForTableToBecomeActive(AmazonDynamoDBClient dynamo, String tableName) {
	long startTime = System.currentTimeMillis();
	long endTime = startTime + (10 * 60 * 1000);
	while (System.currentTimeMillis() < endTime) {
	    try {
		DescribeTableRequest request = new DescribeTableRequest().withTableName(tableName);
		addClientMarker(request);

		TableDescription tableDescription = dynamo.describeTable(request).getTable();
		if (tableDescription == null) continue;

		String tableStatus = tableDescription.getTableStatus();
		if (tableStatus.equals(TableStatus.ACTIVE.toString())) return;
	    } catch (AmazonServiceException ase) {
		if (ase.getErrorCode().equalsIgnoreCase("ResourceNotFoundException") == false)
		    throw ase;
	    }

	    try {
		Thread.sleep(1000 * 5);
	    } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
		throw new AmazonClientException(
			"Interrupted while waiting for table '" + tableName + "' to become active.", e);
	    }
	}

	throw new AmazonClientException("Table '" + tableName + "' never became active");
    }

    public static void createSessionTable(AmazonDynamoDBClient dynamo, String tableName, long readCapacityUnits, long writeCapacityUnits) {
	CreateTableRequest request = new CreateTableRequest().withTableName(tableName);
	addClientMarker(request);

	request.withKeySchema(new KeySchemaElement()
	.withAttributeName(SessionTableAttributes.SESSION_ID_KEY)
	.withKeyType(KeyType.HASH)
	.withAttributeName(SessionTableAttributes.SESSION_SEQ_KEY)
	.withKeyType(KeyType.RANGE));

	request.withAttributeDefinitions(new AttributeDefinition()
	.withAttributeName(SessionTableAttributes.SESSION_ID_KEY)
	.withAttributeType(ScalarAttributeType.S)
	.withAttributeName(SessionTableAttributes.SESSION_SEQ_KEY)
	.withAttributeType(ScalarAttributeType.S));

	request.setProvisionedThroughput(new ProvisionedThroughput()
	.withReadCapacityUnits(readCapacityUnits)
	.withWriteCapacityUnits(writeCapacityUnits));

	dynamo.createTable(request);
    }

    public static void addClientMarker(AmazonWebServiceRequest request) {
	request.getRequestClientOptions().addClientMarker("DynamoSessionManager/1.0");
    }

    private static Map<String, AttributeValue> newAttributeValueMap() {
	//      return new HashMap<String, AttributeValue>();
	return new TreeMap<String, AttributeValue>(); // less space overhead?
    }

}
