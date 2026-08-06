package com.dotblog.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlogCommentDeletedEvent(
        String eventId,
        String blogId,
        String userId,
        String commentId,
        Instant deletedAt
) {
    @JsonCreator
    public BlogCommentDeletedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("blogId") String blogId,
            @JsonProperty("userId") String userId,
            @JsonProperty("commentId") String commentId,
            @JsonProperty("deletedAt") Instant deletedAt
    ) {
        this.eventId = eventId;
        this.blogId = blogId;
        this.userId = userId;
        this.commentId = commentId;
        this.deletedAt = deletedAt != null ? deletedAt : Instant.now();
    }
}
