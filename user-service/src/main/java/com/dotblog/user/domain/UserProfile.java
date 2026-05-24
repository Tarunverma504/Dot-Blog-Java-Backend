package com.dotblog.user.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * View of the shared {@code users} collection (written by auth-service).
 *
 * <p>This document only declares the fields the user-service reads or writes.
 * Updates MUST go through {@code MongoTemplate} with {@code $set}/{@code $push}
 * (never {@code save()}) so the auth-only fields (password, otp, verified...)
 * are not dropped.
 */
@Document(collection = "users")
public class UserProfile {

    @Id
    private String id;
    private String name;
    private String email;
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getProfilePhoto() { return profilePhoto; }
    public void setProfilePhoto(String profilePhoto) { this.profilePhoto = profilePhoto != null ? profilePhoto : ""; }

    public String getProfilePhotoPublicId() { return profilePhotoPublicId; }
    public void setProfilePhotoPublicId(String id) { this.profilePhotoPublicId = id != null ? id : ""; }

    public String getCoverPhoto() { return coverPhoto; }
    public void setCoverPhoto(String coverPhoto) { this.coverPhoto = coverPhoto != null ? coverPhoto : ""; }

    public String getCoverPhotoPublicId() { return coverPhotoPublicId; }
    public void setCoverPhotoPublicId(String id) { this.coverPhotoPublicId = id != null ? id : ""; }

    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about != null ? about : ""; }

    public List<String> getPosts() { return posts; }
    public void setPosts(List<String> posts) { this.posts = posts != null ? posts : new ArrayList<>(); }
}
