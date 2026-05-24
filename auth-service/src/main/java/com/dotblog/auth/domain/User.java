package com.dotblog.auth.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String name;
    @Indexed(unique = true)
    private String email;
    private String password; // hashed with BCrypt
    private String otp;
    private boolean verified = false;

    @Field("profilePhoto")
    private String profilePhoto = "";
    @Field("profilePhoto_Public_ID")
    private String profilePhotoPublicId = "";
    @Field("coverPhoto")
    private String coverPhoto = "";
    @Field("coverPhoto_Public_ID")
    private String coverPhotoPublicId = "";
    private String about = "";

    private List<String> posts = new ArrayList<>();

    @Field("createdAt")
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getProfilePhoto() { return profilePhoto; }
    public void setProfilePhoto(String profilePhoto) { this.profilePhoto = profilePhoto != null ? profilePhoto : ""; }

    public String getProfilePhotoPublicId() { return profilePhotoPublicId; }
    public void setProfilePhotoPublicId(String profilePhotoPublicId) { this.profilePhotoPublicId = profilePhotoPublicId != null ? profilePhotoPublicId : ""; }

    public String getCoverPhoto() { return coverPhoto; }
    public void setCoverPhoto(String coverPhoto) { this.coverPhoto = coverPhoto != null ? coverPhoto : ""; }

    public String getCoverPhotoPublicId() { return coverPhotoPublicId; }
    public void setCoverPhotoPublicId(String coverPhotoPublicId) { this.coverPhotoPublicId = coverPhotoPublicId != null ? coverPhotoPublicId : ""; }

    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about != null ? about : ""; }

    public List<String> getPosts() { return posts; }
    public void setPosts(List<String> posts) { this.posts = posts != null ? posts : new ArrayList<>(); }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt != null ? createdAt : Instant.now(); }
}
