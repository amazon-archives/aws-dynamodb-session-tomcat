The Amazon DynamoDB Session Manager for Tomcat allows you to easily store your
session data from Tomcat web applications in Amazon DynamoDB.

To get started with the DynamoDB Session Manager, follow these steps:
1 - Copy the AmazonDynamoDBSessionManagerForTomcat-1.x.x.jar into your Tomcat install's lib directory
2 - Edit the context.xml file in your Tomcat's conf directory to configure the custom session manager:

<?xml version="1.0" encoding="UTF-8"?>
<Context>
    <WatchedResource>WEB-INF/web.xml</WatchedResource>
    <Manager className="com.amazonaws.services.dynamodb.sessionmanager.DynamoDBSessionManager"
             awsAccessKey="myAwsAccessKey"
             awsSecretKey="myAwsSecretKey"
             createIfNotExist="true" />
</Context>


For more information, including how to use the DynamoDB Session Manager in AWS Elastic Beanstalk,
see the AWS SDK for Java Developer Guide:
http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/welcome.html
