package com.dotblog.blog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Dev-only switches for the Phase 6.1 dual-write chaos demo.
 * Leave these false in any real environment.
 */
@Configuration
@ConfigurationProperties(prefix = "dotblog.chaos")
public class ChaosProperties {

    /**
     * When true, {@code createBlog} throws after the blog+outbox Mongo commit
     * (Phase 6.1 leftover). The outbox relay still publishes the event.
     */
    private boolean failAfterBlogPersist;

    public boolean isFailAfterBlogPersist() {
        return failAfterBlogPersist;
    }

    public void setFailAfterBlogPersist(boolean failAfterBlogPersist) {
        this.failAfterBlogPersist = failAfterBlogPersist;
    }
}
