package com.dotblog.blog.service;

import com.dotblog.blog.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Verifies tokens issued by auth-service (payload: {@code { data: userId }}).
 */
@Service
public class JwtSupport {

    private final SecretKey key;

    public JwtSupport(JwtProperties props) {
        byte[] secretBytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            secretBytes = Arrays.copyOf(secretBytes, 32);
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * Extract userId, treating Node-style strings ({@code "null"}, blanks) as
     * "no token". Returns 401 if required but missing/invalid.
     */
    public String requireUserIdFromHeader(String authorization) {
        String userId = tryUserIdFromHeader(authorization);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
        }
        return userId;
    }

    /** Same as {@link #requireUserIdFromHeader} but returns null instead of throwing. */
    public String tryUserIdFromHeader(String authorization) {
        if (authorization == null || authorization.isBlank() || "null".equals(authorization)) {
            return null;
        }
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : authorization.trim();
        if (token.isEmpty() || "null".equals(token)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object data = claims.get("data");
            return data != null ? data.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
