package com.dotblog.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UserVerifiedEvent(
        String userId,
        String email,
        String name,
        Instant at
) {
    @JsonCreator
    public UserVerifiedEvent(
            @JsonProperty("userId") String userId,
            @JsonProperty("email") String email,
            @JsonProperty("name") String name,
            @JsonProperty("at") Instant at
    ) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.at = at != null ? at : Instant.now();
    }
}
