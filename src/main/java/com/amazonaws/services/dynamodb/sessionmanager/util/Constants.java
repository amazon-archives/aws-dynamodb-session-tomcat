package com.amazonaws.services.dynamodb.sessionmanager.util;

public class Constants {
	public static final String INCOMPLETE_AWS_IN_CONTEXT = "Incomplete AWS security credentials specified in context.xml.";
	public static final String UNABLE_READ_AWS_SECURUTY_CREDENTIALS_IN_CONTEXT= "Unable to read AWS security credentials from file specified in context.xml: ";
	public static final String UNABLE_FIND_AWS_SECURUTY_CREDENTIALS = "Unable to find AWS security credentials. Searched JVM system properties, OS env vars, and EC2 instance roles. Specify credentials in Tomcat's context.xml file or put them in one of the places mentioned above.";
	public static final String INCOMPLETE_PROXY_SETTINGS_IN_CONTEXT = "Incomplete proxy settings specified in context.xml. Both proxy hot and proxy port needs to be specified";
}
