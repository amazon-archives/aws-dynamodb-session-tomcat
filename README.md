Amazon DynamoDB Session Manager for Apache Tomcat
=================================================

Usage Information
-----------------

This project builds on top of the [AWS SDK for Java](http://aws.amazon.com/sdkforjava) 
to provide a session manager for Tomcat 7 that persists session data in [Amazon DynamoDB](http://aws.amazon.com/dynamodb).

You can download release builds of the session manager through the 
[releases section of this project]
(https://github.com/aws/aws-dynamodb-session-tomcat/releases).

For more information on using the session manager, see the 
[session manager section in the AWS SDK for Java Developer Guide]
(http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-tomcat-session-manager.html).  

Developer Information
---------------------

You can check out the source for the session manager here, and build it with Maven.  
The official release builds use JarJar
to package all the dependencies in the session manager jar *(to provide an easy, one-jar install)* and rename classes 
*(to avoid exposing the SDK code to all web apps running in Tomcat)*.  To run with a development build, 
you'll need to copy the SDK third-party dependencies into your Tomcat install's <code>lib</code> directory.

If you encounter problems with the session manager, feel free to report them as GitHub issues for this project.  

**If you'd like to contribute a new feature or bug fix, we'd love to see GitHub pull requests from you!**
