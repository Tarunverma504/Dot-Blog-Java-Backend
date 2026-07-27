package com.dotblog.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Configuration
@ConfigurationProperties(prefix = "dotblog.kafka.topics")
public class KafkaTopicsProperties {
    
    private String otpRequested;
    private String userVerified;

    public String getOtpRequested() {
        return otpRequested;
    }

    public void setOtpRequested(String otpRequested) {
        this.otpRequested = otpRequested;
    }

    public String getUserVerified() {
        return userVerified;
    }

    public void setUserVerified(String userVerified) {
        this.userVerified = userVerified;
    }
}
