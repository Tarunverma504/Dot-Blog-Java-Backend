package com.dotblog.user.service;

import com.dotblog.user.config.BlogClientProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls blog-service for a user's blogs. Best-effort: a network or service
 * failure returns empty lists instead of failing the whole Author response.
 */
@Service
public class BlogClient {

    private final RestClient client;

    private static final ParameterizedTypeReference<Map<String, List<Map<String, Object>>>> BLOGS_MAP =
            new ParameterizedTypeReference<>() {};

    public BlogClient(BlogClientProperties props) {
        this.client = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    /** Returns {@code {Published: [...], Draft: [...]}}. When the upstream fails, both lists are empty. */
    public Map<String, List<Map<String, Object>>> getBlogsForUser(String userId, boolean publishedOnly) {
        try {
            Map<String, List<Map<String, Object>>> body = client.get()
                    .uri("/internal/users/{id}/blogs?publishedOnly={p}", userId, publishedOnly)
                    .retrieve()
                    .body(BLOGS_MAP);
            if (body == null) {
                return Map.of("Published", List.of(), "Draft", List.of());
            }
            return body;
        } catch (Exception e) {
            return Map.of("Published", List.of(), "Draft", List.of());
        }
    }
}
