package com.dotblog.blog.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Matches Node body for {@code POST /api/v2/update-blog/:id}. */
public record UpdateBlogRequest(
        String heading,
        String subText,
        String content,
        String category,
        @JsonProperty("isThumbnailUpdated") Boolean isThumbnailUpdated,
        String url,
        @JsonProperty("public_id") String publicId
) {
}
