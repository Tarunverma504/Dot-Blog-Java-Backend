package com.dotblog.blog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Configuration
@ConfigurationProperties(prefix = "dotblog.kafka.topics")
public class KafkaTopicsProperties {
     private String blogPublished;

     public String getBlogPublished() {
        return blogPublished;
     }

     public void setBlogPublished(String blogPublished) {
        this.blogPublished = blogPublished;
     }
}
