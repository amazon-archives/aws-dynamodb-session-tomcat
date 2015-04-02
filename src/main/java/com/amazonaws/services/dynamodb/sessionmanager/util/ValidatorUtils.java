package com.amazonaws.services.dynamodb.sessionmanager.util;

public class ValidatorUtils {

    public static void nonNull(Object obj, String argName) {
        if (obj == null) {
            throw new IllegalArgumentException(String.format("%s cannot be null", argName));
        }
    }
}
