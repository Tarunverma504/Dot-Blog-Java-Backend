package com.dotblog.blog.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code POST /api/v2/publish-blog}. The field key is intentionally
 * {@code Blogid} (capital B, lowercase i) to match the Node client payload.
 */
public record PublishBlogRequest(
        @JsonProperty("Blogid") String blogId
) {
}
