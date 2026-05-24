package com.dotblog.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Secret key for signing JWT (must match Node: Dot-Blog2_SecretKey). */
    private String secret = "Dot-Blog2_SecretKey";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
