package com.dotblog.shared.contract.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResendOtpRequest(
        @JsonProperty("userId") String userId
) {}
