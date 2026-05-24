package com.dotblog.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.media-service")
public class MediaClientProperties {

    /**
     * Base URL of media-service. Overridden at runtime by
     * {@code MEDIA_SERVICE_URI} (set to {@code http://media-service:8085} in
     * docker-compose). Default here matches application.yml so running the
     * service outside docker (e.g. from an IDE) still works.
     */
    private String baseUrl = "http://localhost:8085";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
