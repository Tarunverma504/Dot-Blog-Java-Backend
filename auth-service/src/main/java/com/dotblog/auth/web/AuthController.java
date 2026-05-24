package com.dotblog.auth.web;

import com.dotblog.auth.service.AuthService;
import com.dotblog.shared.contract.auth.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v2")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@RequestBody VerifyRequest request) {
        AuthResponse response = authService.verify(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resendOtp")
    public ResponseEntity<Map<String, String>> resendOtp(@RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(Map.of("message", "Please check your email for the OTP"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/isAuthenticated")
    public ResponseEntity<AuthResponse> isAuthenticated(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthResponse response = authService.isAuthenticated(authorization);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok("Link send successfully");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        String message = authService.resetPassword(request);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/validate-password-reset-link/{id}")
    public ResponseEntity<Boolean> validateResetLink(@PathVariable String id) {
        boolean expired = authService.validateResetLink(id);
        return ResponseEntity.ok(expired);
    }
}
