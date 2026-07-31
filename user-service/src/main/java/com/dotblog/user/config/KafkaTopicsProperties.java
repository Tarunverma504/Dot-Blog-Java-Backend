package com.dotblog.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Configuration
@ConfigurationProperties(prefix = "dotblog.kafka.topics")
public class KafkaTopicsProperties {
    private String mediaDeletionRequested;

    public String getMediaDeletionRequested() {
        return mediaDeletionRequested;
    }

    public void setMediaDeletionRequested(String mediaDeletionRequested) {
        this.mediaDeletionRequested = mediaDeletionRequested;
    }
}
