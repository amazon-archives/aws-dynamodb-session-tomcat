package com.amazonaws.services.dynamodb.sessionmanager.util;

import org.apache.catalina.Context;
import org.apache.catalina.session.PersistentManagerBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.lang.reflect.Method;

/**
 * Created by atoback on 4/3/17.
 */
public class TomcatUtils {
    private static final Log logger = LogFactory.getLog(TomcatUtils.class);

    public static final String TOMCAT_V7 = "getContainer";
    public static final String TOMCAT_V8 = "getContext";

    public static Context getContext(PersistentManagerBase manager) {
        Context rval = null;

        Method method = null;
        try {
            method = manager.getClass().getMethod(TOMCAT_V8);
        }
        catch (Throwable t) {
            try {
                method = manager.getClass().getMethod(TOMCAT_V7);
            }
            catch (Throwable t2) {
                logger.fatal(t2.getMessage(), t2);
            }
        }

        return invokeMethod(manager, method);
    }

    public static Context invokeMethod(final PersistentManagerBase manager, final Method method) {
        Context rval = null;

        try {
            rval = (Context) method.invoke(manager);
        }
        catch (Throwable t) {
            logger.fatal("Get Context method[" + method.getName() + "] failed", t);
        }

        return rval;
    }
}
