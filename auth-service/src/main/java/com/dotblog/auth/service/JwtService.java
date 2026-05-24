package com.dotblog.auth.service;

import com.dotblog.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * JWT create/verify compatible with Node (payload: { data: userId }).
 * Secret is padded to 32 bytes if shorter (jjwt HS256 requirement).
 */
@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(JwtProperties props) {
        byte[] secretBytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            secretBytes = Arrays.copyOf(secretBytes, 32);
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /** Create token with payload { data: userId } like Node's createToken(userId). */
    public String createToken(String userId) {
        return Jwts.builder()
                .claim("data", userId)
                .signWith(key)
                .compact();
    }

    /** Get userId from token; throws if invalid or expired. */
    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object data = claims.get("data");
        return data != null ? data.toString() : null;
    }
}
