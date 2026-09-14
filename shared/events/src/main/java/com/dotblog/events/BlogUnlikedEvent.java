package com.dotblog.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record BlogUnlikedEvent(
    String eventId,
    String blogId,
    String userId,
    Instant unlikedAt
) {
    @JsonCreator
    public BlogUnlikedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("blogId") String blogId,
        @JsonProperty("userId") String userId,
        @JsonProperty("unlikedAt") Instant unlikedAt
    ) {
        this.eventId = eventId;
        this.blogId = blogId;
        this.userId = userId;
        this.unlikedAt = unlikedAt;
    }
}
