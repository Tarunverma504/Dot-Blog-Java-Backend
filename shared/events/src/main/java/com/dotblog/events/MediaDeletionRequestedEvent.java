package com.dotblog.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MediaDeletionRequestedEvent(
    String eventId,
    String publicId,
    String reason,
    Instant requestedAt
){
    @JsonCreator
    public MediaDeletionRequestedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("publicId") String publicId,
        @JsonProperty("reason") String reason,
        @JsonProperty("requestedAt") Instant requestedAt
    ){
        this.eventId = eventId;
        this.publicId = publicId;
        this.reason = reason;
        this.requestedAt = requestedAt != null ? requestedAt : Instant.now();
    }
}
