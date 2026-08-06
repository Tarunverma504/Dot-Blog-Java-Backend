package com.dotblog.blog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Configuration
@ConfigurationProperties(prefix = "dotblog.kafka.topics")
public class KafkaTopicsProperties {
     private String blogPublished;
     private String mediaDeletionRequested;
     private String blogLiked;
     private String blogUnliked;
     private String blogCommented;
     private String blogCommentDeleted;

     public String getBlogPublished() {
        return blogPublished;
     }

     public void setBlogPublished(String blogPublished) {
        this.blogPublished = blogPublished;
     }

     public String getMediaDeletionRequested() {
        return mediaDeletionRequested;
     }

     public void setMediaDeletionRequested(String mediaDeletionRequested) {
        this.mediaDeletionRequested = mediaDeletionRequested;
     }
     public String getBlogLiked() {
        return blogLiked;
     }

     public void setBlogLiked(String blogLiked) {
        this.blogLiked = blogLiked;
     }

     public String getBlogUnliked() {
        return blogUnliked;
     }

     public void setBlogUnliked(String blogUnliked) {
        this.blogUnliked = blogUnliked;
     }

     public String getBlogCommented() {
        return blogCommented;
     }

     public void setBlogCommented(String blogCommented) {
        this.blogCommented = blogCommented;
     }

     public String getBlogCommentDeleted() {
        return blogCommentDeleted;
     }

     public void setBlogCommentDeleted(String blogCommentDeleted) {
        this.blogCommentDeleted = blogCommentDeleted;
     }
}
