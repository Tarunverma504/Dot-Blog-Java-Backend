package com.dotblog.blog.service;

import com.dotblog.blog.config.UserClientProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Thin RestClient over user-service for the two cross-service operations
 * blog-service needs in Day 4:
 *  - read author profile (for {@code GET /api/v2/get-blog/:id} response)
 *  - append a new blog id to {@code user.posts} after create-blog-save
 *
 * <p>Failures are intentionally non-fatal where possible — we'd rather return
 * a blog with empty author fields than 500 the whole request if user-service
 * is briefly unavailable.
 */
@Component
public class UserClient {

    private final RestClient http;

    public UserClient(UserClientProperties props) {
        this.http = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }

    public Optional<AuthorView> getAuthor(String userId) {
        try {
            AuthorView view = http.get()
                    .uri("/api/v2/Author/{id}", userId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AuthorView.class);
            return Optional.ofNullable(view);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Single-user summary, convenience over {@link #summariesByIds}. */
    public Optional<UserSummary> getSummary(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        Map<String, UserSummary> map = summariesByIds(java.util.List.of(userId));
        return Optional.ofNullable(map.get(userId));
    }

    /**
     * Best-effort append. Throws on hard failure so the caller can decide
     * whether to roll back the blog insert.
     */
    public void appendPost(String userId, String blogId) {
        http.post()
                .uri("/internal/users/{id}/posts", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("blogId", blogId))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Batch user summary used to populate {@code blog.userId} on lists and
     * {@code comments.userId} on read responses. Returns empty map on error.
     */
    public Map<String, UserSummary> summariesByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
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

    /** Minimal projection of user-service's {@code AuthorResponse}. */
    public record AuthorView(
            String name,
            String profilePhoto,
            String coverPhoto,
            String about,
            @JsonProperty("Published") java.util.List<Object> published,
            @JsonProperty("Draft") java.util.List<Object> draft
    ) {
    }

    /** Matches user-service's {@code UserSummary}. */
    public record UserSummary(
            @JsonProperty("_id") String id,
            String name,
            String email,
            String profilePhoto
    ) {
    }
}
