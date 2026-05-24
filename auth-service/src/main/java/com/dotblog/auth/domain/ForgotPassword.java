package com.dotblog.auth.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "forgot_passwords")
public class ForgotPassword {

    @Id
    private String id;

    @Field("userId")
    private String userId;
    private String email;
    private boolean expired = false;

    @Field("createdAt")
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt != null ? createdAt : Instant.now(); }
}
