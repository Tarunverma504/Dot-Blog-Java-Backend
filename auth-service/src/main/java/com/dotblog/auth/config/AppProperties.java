package com.dotblog.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String frontendUrl = "http://localhost:3000";

    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String frontendUrl) { this.frontendUrl = frontendUrl != null ? frontendUrl : "http://localhost:3000"; }
}
