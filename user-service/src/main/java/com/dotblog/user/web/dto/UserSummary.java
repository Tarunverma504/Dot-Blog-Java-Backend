package com.dotblog.user.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lean view of a user used by other services (e.g. blog/engagement) to
 * populate {@code comments.userId} and {@code blog.userId}. Matches what
 * Node mongoose populated with {@code 'name email profilePhoto'}.
 */
public record UserSummary(
        @JsonProperty("_id") String id,
        String name,
        String email,
        String profilePhoto
) {
}
