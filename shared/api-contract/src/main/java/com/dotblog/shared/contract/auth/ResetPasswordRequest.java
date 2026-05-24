package com.dotblog.shared.contract.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResetPasswordRequest(
        String password,
        @JsonProperty("confirmPassword") String confirmPassword,
        String id
) {}
