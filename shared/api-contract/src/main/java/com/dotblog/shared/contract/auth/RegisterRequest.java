package com.dotblog.shared.contract.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Please enter your name")
        @Size(max = 30)
        String username,

        @NotBlank(message = "Please enter your email")
        @Email(message = "Please enter valid email address")
        String email,

        @NotBlank(message = "Please enter your password")
        String password
) {}
