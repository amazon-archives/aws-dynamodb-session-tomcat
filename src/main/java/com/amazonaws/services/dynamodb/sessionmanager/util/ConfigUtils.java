package com.amazonaws.services.dynamodb.sessionmanager.util;

import com.amazonaws.services.dynamodb.sessionmanager.DynamoDBSessionManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Created by atoback on 4/5/17.
 */
public class ConfigUtils {
    private static final Log logger = LogFactory.getLog(ConfigUtils.class);

    // Table name is a constant because of the Table Annotation in DynamoSessionItem
    public static final String DEFAULT_TABLE_NAME            = "Tomcat_SessionState";

    public static final String CONFIG_PROPERTIES_FILE_NAME   = "aws_dynamodb_tomcat.properties";

    public static final String VERSION                       = "aws.dynamodb.mgr.version";
    public static final String USER_AGENT                    = "aws.dynamodb.mgr.user.agent";
    public static final String NAME                          = "aws.dynamodb.mgr.name";

    public static final String DISABLE_REAPER                = "aws.dynamodb.mgr.disable.reaper";
    public static final String DELETE_CORRUPTED_SESSIONS     = "aws.dynamodb.mgr.delete.corrupted.sessions";
    public static final String CREATE_IF_NOT_EXIST           = "aws.dynamodb.mgr.create.if.not.exists";
    public static final String SAVE_ON_RESTART               = "aws.dynamodb.mgr.save.on.restart";
    public static final String MAX_IDLE_BACKUP               = "aws.dynamodb.mgr.max.idle.backup";
    public static final String READ_CAPACITY_UNITS           = "aws.dynamodb.mgr.read.capacity.units";
    public static final String WRITE_CAPACITY_UNITS          = "aws.dynamodb.mgr.write.capacity.units";
    public static final String REGION_ID                     = "aws.dynamodb.mgr.region.id";


    private static final Properties configProperties;
    static {
        configProperties = getConfigProperties(CONFIG_PROPERTIES_FILE_NAME);
    };

    public static int getInt(String key) {
        return getInt(key, 0);
    }

    public static int getInt(String key, int defaultValue) {
        int rval = defaultValue;

        try {
            rval = Integer.parseInt(configProperties.getProperty(key));
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return rval;
    }

    public static long getLong(String key) {
        return getLong(key, 0L);
    }

    public static long getLong(String key, long defaultValue) {
        long rval = defaultValue;

        try {
            rval = Long.parseLong(configProperties.getProperty(key));
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return rval;
    }

    public static String getString(String key) {
        return getString(key, "");
    }

    public static String getString(String key, String defaultValue) {
        return configProperties.getProperty(key, defaultValue);
    }

    public static boolean getBoolean(String key) {
        return getBoolean(key, Boolean.FALSE);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(
                configProperties.getProperty(key, String.valueOf(defaultValue))
        );
    }

    public static void addProperty(final String key, final String value) {
        configProperties.put(key, value);
    }

    public static void addConfigProperties(final String fileName) {
        configProperties.putAll(getConfigProperties(fileName));
    }

    public static Properties getConfigProperties(final String fileName) {
        Properties properties = new Properties();

        try {
            ClassLoader classLoader = ConfigUtils.class.getClassLoader();
            InputStream input = classLoader.getResourceAsStream(fileName);
            properties.load(input);
            input.close();
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return properties;
    }
}
