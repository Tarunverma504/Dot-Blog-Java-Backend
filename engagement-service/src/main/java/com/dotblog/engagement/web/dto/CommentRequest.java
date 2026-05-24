package com.dotblog.engagement.web.dto;

/**
 * Body of {@code /add-commnet}. Like {@link LikeRequest}, {@code userId}
 * is the raw JWT.
 */
public record CommentRequest(String BlogId, String userId, String Comment) {
}
