package com.dotblog.blog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the Node {@code Blog} schema (preserving the original mixed-case
 * field names so the React client and any legacy data remain compatible).
 */
@Document(collection = "blogs")
public class Blog {

    @Id
    private String id;

    private String userId;

    @Field("Thumbnail")
    private String thumbnail = "";

    @Field("Thumbnail_PublicId")
    private String thumbnailPublicId = "";

    @Field("Title")
    private String title = "";

    @Field("SubText")
    private String subText = "";

    @Field("Body")
    private String body = "";

    @Field("Category")
    private String category = "";

    private boolean isPublished = false;

    /**
     * Soft-delete flag. When {@code true} the blog is hidden from every read
     * endpoint (lists, single-blog fetch, owner dashboard). The document is
     * preserved in Mongo for forensics / admin restore. Absent on legacy docs,
     * which is treated as {@code false} via {@code Criteria.where("hidden").ne(true)}.
     */
    private boolean hidden = false;

    private List<Like> likes = new ArrayList<>();

    private List<Comment> comments = new ArrayList<>();

    @Field("PublishedDate")
    private Instant publishedDate = Instant.now();

    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail != null ? thumbnail : ""; }

    public String getThumbnailPublicId() { return thumbnailPublicId; }
    public void setThumbnailPublicId(String id) { this.thumbnailPublicId = id != null ? id : ""; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title != null ? title : ""; }

    public String getSubText() { return subText; }
    public void setSubText(String subText) { this.subText = subText != null ? subText : ""; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body != null ? body : ""; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category != null ? category : ""; }

    public boolean isPublished() { return isPublished; }
    public void setPublished(boolean published) { this.isPublished = published; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public List<Like> getLikes() { return likes; }
    public void setLikes(List<Like> likes) { this.likes = likes != null ? likes : new ArrayList<>(); }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments != null ? comments : new ArrayList<>(); }

    public Instant getPublishedDate() { return publishedDate; }
    public void setPublishedDate(Instant publishedDate) { this.publishedDate = publishedDate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt != null ? createdAt : Instant.now(); }

    public static class Like {
        private String userId;
        public Like() {}
        public Like(String userId) { this.userId = userId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }

    public static class Comment {
        @org.springframework.data.mongodb.core.mapping.Field("_id")
        private String id;
        private String userId;
        private String text;
        private Instant createdAt = Instant.now();
        public Comment() {}
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }
}
