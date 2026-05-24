package com.dotblog.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.blog-service")
public class BlogClientProperties {

    /**
     * Base URL of blog-service. Overridden at runtime by
     * {@code BLOG_SERVICE_URI} (set to {@code http://blog-service:8083} in
     * docker-compose). Default here matches application.yml so running the
     * service outside docker (e.g. from an IDE) still works.
     */
    private String baseUrl = "http://localhost:8083";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
