package com.dotblog.engagement.service;

import com.dotblog.engagement.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
     * Extract userId from a raw token value (NOT prefixed with {@code Bearer}).
     * The Node client sends the JWT itself as the {@code userId} field on
     * {@code /like-post}, {@code /dislike-post}, and {@code /add-commnet};
     * see {@code postController.js getTokenValue}.
     */
    public String userIdFromToken(String token) {
        if (token == null || token.isBlank() || "null".equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
        }
        String t = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
        if (t.isEmpty() || "null".equals(t)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(t)
                    .getPayload();
            Object data = claims.get("data");
            if (data == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
            }
            return data.toString();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
        }
    }
}
