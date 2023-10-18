package com.prannav.learning.aws.apps.func;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RekognitionFunction implements RequestHandler<S3Event, Void> {
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final Region REGION = Region.of(System.getenv("REGION_NAME"));
    final RekognitionClient rekognitionClient = RekognitionClient.builder()
                                                                 .region(REGION)
                                                                 .build();
    final DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                                                        .region(REGION)
                                                        .build();

    @Override
    public Void handleRequest(S3Event s3Event, Context context) {
        final LambdaLogger logger = context.getLogger();
        final List<S3EventNotification.S3EventNotificationRecord> records = s3Event.getRecords();
        if (CollectionUtils.isNullOrEmpty(records)) {
            logger.log("No Event Record");
            return null;
        }

        logger.log("Got notification for " + records.size());
        records.forEach(record -> {
            final String region = record.getAwsRegion();
            final S3EventNotification.S3Entity s3 = record.getS3();
            final String bucket = s3.getBucket()
                                    .getName();
            final String key = s3.getObject()
                                 .getKey();
            logger.log("Processing Event: " + record.getEventName()
                    + " for bucket " + bucket + " and key " + key + " in " + region + " region.");
            final S3Object s3Object = S3Object.builder()
                                              .bucket(bucket)
                                              .name(key)
                                              .build();
            final Image reckogImage = Image.builder()
                                           .s3Object(s3Object)
                                           .build();
            final DetectLabelsRequest detectLabelRequest = DetectLabelsRequest.builder()
                                                                              .image(reckogImage)
                                                                              .build();
            final DetectLabelsResponse rekogResponse = rekognitionClient.detectLabels(detectLabelRequest);
            final List<AttributeValue> labels = rekogResponse.labels()
                                                             .stream()
                                                             .map(label -> AttributeValue.builder()
                                                                                         .s(label.name())
                                                                                         .build())
                                                             .collect(Collectors.toList());

            final Map<String, AttributeValue> item = new HashMap<>();
            item.put("imageId", AttributeValue.builder()
                                              .s(key)
                                              .build());
            item.put("imageLabels", AttributeValue.builder()
                                                  .l(labels)
                                                  .build());
            // can be batched
            PutItemResponse dynamoResponse = dynamoDbClient.putItem(PutItemRequest.builder()
                                                                                  .item(item)
                                                                                  .tableName(TABLE_NAME)
                                                                                  .build());
            logger.log("Saved to Dynamo: " + dynamoResponse.toString());

        });

        return null;
    }
}
