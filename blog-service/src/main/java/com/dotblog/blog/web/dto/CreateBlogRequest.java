package com.dotblog.blog.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Matches Node body for {@code POST /api/v2/create-blog-save}. */
public record CreateBlogRequest(
        String heading,
        String subText,
        String content,
        String category,
        String url,
        @JsonProperty("public_id") String publicId
) {
}
