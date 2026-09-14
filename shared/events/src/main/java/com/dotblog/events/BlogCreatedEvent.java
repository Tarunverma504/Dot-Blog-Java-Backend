package com.dotblog.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlogCreatedEvent(
        String eventId,
        String blogId,
        String userId,
        String title,
        Instant createdAt
) {
    @JsonCreator
    public BlogCreatedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("blogId") String blogId,
            @JsonProperty("userId") String userId,
            @JsonProperty("title") String title,
            @JsonProperty("createdAt") Instant createdAt
    ) {
        this.eventId = eventId;
        this.blogId = blogId;
        this.userId = userId;
        this.title = title;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
