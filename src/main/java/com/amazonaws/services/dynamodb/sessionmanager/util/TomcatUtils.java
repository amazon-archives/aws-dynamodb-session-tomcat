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

        Method[] methods = manager.getClass().getMethods();
        for (Method method : methods) {
            if (method.getName().equals(TOMCAT_V7) || method.getName().equals(TOMCAT_V8)) {
                rval = invokeMethod(manager, method);
                break;
            }
        }

        return rval;
    }

    public static Context invokeMethod(final PersistentManagerBase manager, final Method method) {
        Context rval = null;

        try {
           rval = (Context) method.invoke(manager, new Object[]{null});
        }
        catch (Exception e) {
            logger.fatal("Get Context method[" + method.getName() + "] failed", e);
        }

        return rval;
    }
}
