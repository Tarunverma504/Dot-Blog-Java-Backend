package com.dotblog.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record BlogLikedEvent(
    String eventId,
    String blogId,
    String userId,
    Instant likedAt
) {
    @JsonCreator
    public BlogLikedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("blogId") String blogId,
        @JsonProperty("userId") String userId,
        @JsonProperty("likedAt") Instant likedAt
    ) {
        this.eventId = eventId;
        this.blogId = blogId;
        this.userId = userId;
        this.likedAt = likedAt;
    }
}