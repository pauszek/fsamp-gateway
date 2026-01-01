package io.github.pauszek.fsampgateway.adapter.out.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for SNS publisher.
 */
@ConfigurationProperties(prefix = "aws.sns")
public class SnsPublisherProperties {

    private String fileEventsTopicArn;

    public String getFileEventsTopicArn() {
        return fileEventsTopicArn;
    }

    public void setFileEventsTopicArn(String fileEventsTopicArn) {
        this.fileEventsTopicArn = fileEventsTopicArn;
    }
}
