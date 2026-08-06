package com.dotblog.engagement.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dotblog.kafka.topics")
public class KafkaTopicsProperties {
    private String blogLiked;
    private String blogUnliked;
    private String blogCommented;
    private String blogCommentDeleted;

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
