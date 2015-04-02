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

import java.util.Collections;
import java.util.Iterator;

import org.apache.catalina.Session;

import com.amazonaws.services.dynamodb.sessionmanager.converters.SessionConverter;
import com.amazonaws.services.dynamodb.sessionmanager.util.ValidatorUtils;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;

public class DynamoSessionStorage {

    private final DynamoDBMapper mapper;
    private final SessionConverter sessionConverter;

    public DynamoSessionStorage(DynamoDBMapper dynamoMapper, SessionConverter sessionConverter) {
        ValidatorUtils.nonNull(dynamoMapper, "DynamoDBMapper");
        ValidatorUtils.nonNull(sessionConverter, "SessionConverter");
        this.mapper = dynamoMapper;
        this.sessionConverter = sessionConverter;
    }

    public int count() {
        return mapper.count(DynamoSessionItem.class, new DynamoDBScanExpression());
    }

    public Session loadSession(String sessionId) {
        DynamoSessionItem sessionItem = mapper.load(new DynamoSessionItem(sessionId));
        if (sessionItem != null) {
            return sessionConverter.toSession(sessionItem);
        } else {
            return null;
        }
    }

    public void deleteSession(String sessionId) {
        mapper.delete(new DynamoSessionItem(sessionId));
    }

    public void saveSession(Session session) {
        mapper.save(sessionConverter.toSessionItem(session));
    }

    public Iterable<Session> listSessions() {
        PaginatedScanList<DynamoSessionItem> sessions = mapper.scan(DynamoSessionItem.class,
                new DynamoDBScanExpression());
        return new SessionConverterIterable(sessions);
    }

    private class SessionConverterIterable implements Iterable<Session> {

        private final Iterable<DynamoSessionItem> sessionIterable;

        private SessionConverterIterable(Iterable<DynamoSessionItem> sessionIterable) {
            this.sessionIterable = sessionIterable;
        }

        @Override
        public Iterator<Session> iterator() {
            return new SessionConverterIterator(getIteratorSafe(sessionIterable));
        }

        /**
         * Returns either the Iterator for a given Iterable or an empty Iterator but not null.
         */
        private <T> Iterator<T> getIteratorSafe(Iterable<T> iterable) {
            if (iterable != null) {
                return iterable.iterator();
            } else {
                return Collections.<T> emptyList().iterator();
            }
        }

    }

    /**
     * Custom iterator to convert a {@link DynamoSessionItem} to a Tomcat {@link Session} before
     * returning it
     */
    private class SessionConverterIterator implements Iterator<Session> {

        private final Iterator<DynamoSessionItem> sessionItemterator;

        private SessionConverterIterator(Iterator<DynamoSessionItem> sessionItemIterator) {
            this.sessionItemterator = sessionItemIterator;
        }

        @Override
        public boolean hasNext() {
            return sessionItemterator.hasNext();
        }

        @Override
        public Session next() {
            return sessionConverter.toSession(sessionItemterator.next());
        }

        @Override
        public void remove() {
            sessionItemterator.remove();
        }

    }

}
