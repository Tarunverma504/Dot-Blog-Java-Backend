package com.dotblog.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Returns error body in same shape as Node: { message: "..." } for 4xx/5xx.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : err.getField())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Internalserver error"));
    }
}
