package com.dotblog.blog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Same secret as auth-service. Default matches Node ("Dot-Blog2_SecretKey"). */
    private String secret = "Dot-Blog2_SecretKey";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
