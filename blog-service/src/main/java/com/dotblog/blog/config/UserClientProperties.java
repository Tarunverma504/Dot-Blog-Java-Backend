package com.dotblog.blog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.user-service")
public class UserClientProperties {

    /** Base URL of user-service, e.g. http://user-service:8082 in docker. */
    private String baseUrl = "http://localhost:8082";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
