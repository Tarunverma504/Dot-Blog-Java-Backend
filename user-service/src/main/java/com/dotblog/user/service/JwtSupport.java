package com.dotblog.user.service;

import com.dotblog.user.config.JwtProperties;
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
 * Secret is padded to 32 bytes (jjwt HS256 requirement) just like auth-service.
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

    /** Extract the userId from an {@code Authorization} header value; 401s if invalid. */
    public String userIdFromHeader(String authorization) {
        if (authorization == null || authorization.isBlank() || "null".equals(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
        }
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : authorization.trim();
        if (token.isEmpty() || "null".equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
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
