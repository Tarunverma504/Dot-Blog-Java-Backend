package com.dotblog.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SendOtpEvent(
        String email,
        String otpMessage
) {
    @JsonCreator
    public SendOtpEvent(
            @JsonProperty("email") String email,
            @JsonProperty("otpMessage") String otpMessage
    ) {
        this.email = email;
        this.otpMessage = otpMessage;
    }
}
