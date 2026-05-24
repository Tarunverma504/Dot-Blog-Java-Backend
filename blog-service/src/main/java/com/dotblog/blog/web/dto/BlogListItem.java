package com.dotblog.blog.web.dto;

import com.dotblog.blog.domain.Blog;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Shape returned by {@code /get-all-blogs}, {@code /get-blogs},
 * {@code /get-categories-blogs/:category}, and the Published/Draft arrays of
 * {@code /get-user-blogs}.
 *
 * <p>Mirrors a Node {@code Blog.find().populate('userId')} document — the
 * {@code userId} field is the populated author object, all other fields keep
 * their original mixed-case names so the React client stays unchanged.
 */
public record BlogListItem(
        @JsonProperty("_id") String id,
        AuthorRef userId,
        @JsonProperty("Thumbnail") String thumbnail,
        @JsonProperty("Thumbnail_PublicId") String thumbnailPublicId,
        @JsonProperty("Title") String title,
        @JsonProperty("SubText") String subText,
        @JsonProperty("Body") String body,
        @JsonProperty("Category") String category,
        boolean isPublished,
        List<Blog.Like> likes,
        List<Blog.Comment> comments,
        @JsonProperty("PublishedDate") Instant publishedDate,
        Instant createdAt
) {
    public record AuthorRef(
            @JsonProperty("_id") String id,
            String name,
            String profilePhoto
    ) {
        public static AuthorRef unknown(String id) {
            return new AuthorRef(id, "", "");
        }
    }
}
