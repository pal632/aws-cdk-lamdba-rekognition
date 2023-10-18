package com.prannav.learning.aws.cdk.infra;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.S3EventSource;
import software.amazon.awscdk.services.s3.*;
import software.amazon.awscdk.services.s3.notifications.LambdaDestination;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class InfraStack extends Stack {
    private static final String BUCKET_NAME = "testCDKDemo_10-17-2023";
    private static final List<String> LAMBDA_ROLE_ACTIONS = Arrays.asList("rekognition:*",
            "logs:CreateLogGroup",
            "logs:CreateLogStream",
            "logs:PutLogEvents");

    public InfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        // Bucket
        final Bucket bucket = Bucket.Builder.create(this, BUCKET_NAME)
                                            .removalPolicy(RemovalPolicy.DESTROY)
                                            .build();

        // Setup DynamoDB
        final Attribute primary = Attribute.builder()
                                           .name("imageId")
                                           .type(AttributeType.STRING)
                                           .build();

        final Table dynamoDBTable = Table.Builder.create(this, "Images")
                                                 .tableName("Images")
                                                 .partitionKey(primary)
                                                 .removalPolicy(RemovalPolicy.DESTROY)
                                                 .build();

        // setup lambda with needed permissions
        final PolicyStatement lambdaPolStatement = PolicyStatement.Builder.create()
                                                                          .effect(Effect.ALLOW)
                                                                          .actions(LAMBDA_ROLE_ACTIONS)
                                                                          .resources(Collections.singletonList("*"))
                                                                          .build();

        final Role lambdaRole = Role.Builder.create(this, "TestCDKDemoLambdaIAMRole")
                                            .roleName("TestCDKDemoLambdaIAMRole")
                                            .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                                            .build();
        lambdaRole.addToPolicy(lambdaPolStatement);

        final Function lambda = Function.Builder.create(this, "TestCDKDemoLambdaFunction")
                                                .code(Code.fromAsset("../apps/RekognitionFunc/target/rekognition-func.jar"))
                                                .handler("com.prannav.learning.aws.apps.func.RekognitionFunction::handleRequest")
                                                .role(lambdaRole)
                                                .runtime(Runtime.JAVA_8)
                                                .memorySize(1024)
                                                .timeout(Duration.seconds(30))
                                                .build();
        // Provide environment variables to Lambda
        lambda.addEnvironment("TABLE_NAME",  dynamoDBTable.getTableName());
        lambda.addEnvironment("REGION_NAME", this.getRegion());

        // set up S3 Create notification to lambda
        final LambdaDestination lambdaDestination = new LambdaDestination(lambda);
        bucket.addEventNotification(EventType.OBJECT_CREATED, lambdaDestination);

        // give lambda access to Dynamo Table and S3 Bucket
        dynamoDBTable.grantReadWriteData(lambda);
        bucket.grantReadWrite(lambda);

    }
}
