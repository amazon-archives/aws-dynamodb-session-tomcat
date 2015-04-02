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

import static org.junit.Assert.assertEquals;

import java.util.Enumeration;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.http.HttpSession;

import org.apache.catalina.Session;

public class CustomAsserts {

    public static void assertSessionEquals(Session expectedSession, Session actualSession) {
        assertEquals(expectedSession.getIdInternal(), actualSession.getIdInternal());
        assertEquals(expectedSession.getCreationTimeInternal(), actualSession.getCreationTimeInternal());
        assertEquals(expectedSession.getLastAccessedTimeInternal(), actualSession.getLastAccessedTimeInternal());
        assertSessionDataEquals(expectedSession.getSession(), actualSession.getSession());
    }

    public static void assertSessionDataEquals(HttpSession expectedSession, HttpSession actualSession) {
        SortedSet<String> expectedAttributeNames = toSortedSet(expectedSession.getAttributeNames());
        SortedSet<String> actualAttributeNames = toSortedSet(actualSession.getAttributeNames());
        assertEquals(expectedAttributeNames, actualAttributeNames);
        for (String attributeName : expectedAttributeNames) {
            assertEquals(expectedSession.getAttribute(attributeName), actualSession.getAttribute(attributeName));
        }
    }

    private static <T> SortedSet<T> toSortedSet(Enumeration<T> enumeration) {
        SortedSet<T> list = new TreeSet<T>();
        while (enumeration.hasMoreElements()) {
            list.add(enumeration.nextElement());
        }
        return list;
    }

}
