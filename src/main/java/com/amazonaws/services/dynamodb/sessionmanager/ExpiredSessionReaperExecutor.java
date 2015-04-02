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

import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A background process to periodically scan and remove any expired session data from the session
 * table in Amazon DynamoDB.
 */
public class ExpiredSessionReaperExecutor {

    private static final int REAP_FREQUENCY_HOURS = 12;
    private static final int MAX_JITTER_HOURS = 5;
    private static final String THREAD_NAME = "dynamo-session-manager-expired-sesion-reaper";

    private final ScheduledThreadPoolExecutor executor;

    public ExpiredSessionReaperExecutor(Runnable expiredSessionRunnable) {
        int initialDelay = new Random().nextInt(MAX_JITTER_HOURS) + 1;
        executor = new ScheduledThreadPoolExecutor(1, new ExpiredSessionReaperThreadFactory());
        executor.scheduleAtFixedRate(expiredSessionRunnable, initialDelay, REAP_FREQUENCY_HOURS, TimeUnit.HOURS);
    }

    /**
     * Shuts down the expired session reaper.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * ThreadFactory for creating the daemon reaper thread.
     */
    private final class ExpiredSessionReaperThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(THREAD_NAME);
            return thread;
        }
    }
}
