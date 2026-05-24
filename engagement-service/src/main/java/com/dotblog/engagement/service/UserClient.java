package com.dotblog.engagement.service;

import com.dotblog.engagement.config.UserClientProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.Map;

@Component
public class UserClient {

    private final RestClient http;

    public UserClient(UserClientProperties props) {
        this.http = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    public Map<String, UserSummary> summariesByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        try {
            Map<String, UserSummary> body = http.post()
                    .uri("/internal/users/summary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ids", ids))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, UserSummary>>() {});
            return body != null ? body : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    public record UserSummary(
            @JsonProperty("_id") String id,
            String name,
            String email,
            String profilePhoto
    ) {
    }
}
