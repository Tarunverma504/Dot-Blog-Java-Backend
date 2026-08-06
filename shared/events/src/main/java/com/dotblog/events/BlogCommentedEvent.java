package com.dotblog.events;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record BlogCommentedEvent(
    String eventId,
    String blogId,
    String userId,
    String comment,
    String commentId,
    Instant commentedAt
) {
    @JsonCreator
    public BlogCommentedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("blogId") String blogId,
        @JsonProperty("userId") String userId,
        @JsonProperty("comment") String comment,
        @JsonProperty("commentId") String commentId,
        @JsonProperty("commentedAt") Instant commentedAt
    ) {
        this.eventId = eventId;
        this.blogId = blogId;
        this.userId = userId;
        this.comment = comment;
        this.commentId = commentId;
        this.commentedAt = commentedAt != null ? commentedAt : Instant.now();
    }
}
