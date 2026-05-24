package com.dotblog.shared.contract.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerifyRequest(
        @JsonProperty("verificationToken") String verificationToken,
        @JsonProperty("Otp") String otp
) {}
