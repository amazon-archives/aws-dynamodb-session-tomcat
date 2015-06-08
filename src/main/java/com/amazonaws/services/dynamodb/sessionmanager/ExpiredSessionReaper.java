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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.Session;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.amazonaws.services.dynamodb.sessionmanager.util.ValidatorUtils;

/**
 * Scans Session table and deletes any sessions that have expired
 */
public class ExpiredSessionReaper implements Runnable {


    private static final Log logger = LogFactory.getLog(ExpiredSessionReaper.class);

    private final DynamoSessionStorage sessionStorage;

    public ExpiredSessionReaper(DynamoSessionStorage sessionStorage) {
        ValidatorUtils.nonNull(sessionStorage, "SessionStorage");
        this.sessionStorage = sessionStorage;
    }

    /**
     * Scans the session table for expired sessions and deletes them.
     */
    @Override
    public void run() {
		try {
			Calendar calendar = Calendar.getInstance();
			String today = new SimpleDateFormat("yyyy-MM-dd").format(calendar
					.getTime());
			executeSessionReaper(today);

			calendar.add(Calendar.DATE, -1);
			String yesterday = new SimpleDateFormat("yyyy-MM-dd")
					.format(calendar.getTime());
			executeSessionReaper(yesterday);
		} catch (Throwable t) {
			logger.warn("ExpiredSessionReaper#run", t);
		}
    }

    private void executeSessionReaper(String hashKey) {
    	DynamoSessionItem item = new DynamoSessionItem();
    	item.setExpiredDate(hashKey);
    	List<DynamoSessionItem> sessions = sessionStorage.listExpiredSessions(item);
    	for(DynamoSessionItem session: sessions) {
        	sessionStorage.deleteSession(session.getSessionId());
    	}
    }

    public static boolean isExpired(Session session) {
        if (canSessionExpire(session)) {
            return session.getLastAccessedTimeInternal() < getInactiveCutoffTime(session);
        }
        return false;
    }

    /**
     * Sessions with a negative max inactive time never expire
     */
    private static boolean canSessionExpire(Session session) {
        return session.getMaxInactiveInterval() > 0;
    }

    /**
     * Any sessions whose access time is older than the cutoff time are considered inactive, those
     * with access time after the cutoff time are still active
     */
    private static long getInactiveCutoffTime(Session session) {
        return System.currentTimeMillis()
                - TimeUnit.MILLISECONDS.convert(session.getMaxInactiveInterval(), TimeUnit.SECONDS);
    }

}