package com.dotblog.engagement.web.dto;

/** Body of {@code /delete-comment}. */
public record DeleteCommentRequest(String commentId, String blogId) {
}
