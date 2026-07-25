package com.dotblog.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Configuration
@ConfigurationProperties(prefix = "dotblog.kafka.topics")
public class KafkaTopicsProperties {
    
    private String otpRequested;

    public String getOtpRequested() {
        return otpRequested;
    }

    public void setOtpRequested(String otpRequested) {
        this.otpRequested = otpRequested;
    }
}
