package com.dotblog.shared.contract.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String name,
        String profilePhoto,
        String coverPhoto,
        @JsonProperty("authToken") String authToken,
        @JsonProperty("userId") Object userId,
        @JsonProperty("verificationToken") String verificationToken
) {
    /** Login / isAuthenticated / verify OTP response */
    public static AuthResponse of(String name, String profilePhoto, String coverPhoto, String authToken, Object userId) {
        return new AuthResponse(name, profilePhoto, coverPhoto, authToken, userId, null);
    }

    /** Register response – verification token only */
    public static AuthResponse verification(String verificationToken) {
        return new AuthResponse(null, null, null, null, null, verificationToken);
    }
}
