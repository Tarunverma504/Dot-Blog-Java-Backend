package com.dotblog.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlogPublishedEvent(
        String eventId,
        String blogId,
        String userId,
        String title,
        String category,
        Instant publishedAt) {

    @JsonCreator
    public BlogPublishedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("blogId") String blogId,
            @JsonProperty("userId") String userId,
            @JsonProperty("title") String title,
            @JsonProperty("category") String category,
            @JsonProperty("publishedAt") Instant publishedAt) {
        this.eventId = eventId;
        this.blogId = blogId;
        this.userId = userId;
        this.title = title;
        this.category = category;
        this.publishedAt = publishedAt != null ? publishedAt : Instant.now();
    }
}