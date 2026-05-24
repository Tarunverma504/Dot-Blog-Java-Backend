package com.dotblog.blog.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Shape returned by {@code GET /api/v2/get-user-blogs}, matches Node response exactly. */
public record UserBlogsResponse(
        String name,
        String profilePhoto,
        String coverPhoto,
        String about,
        @JsonProperty("Published") List<BlogListItem> published,
        @JsonProperty("Draft") List<BlogListItem> draft
) {
}
