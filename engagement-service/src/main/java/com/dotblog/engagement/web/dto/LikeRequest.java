package com.dotblog.engagement.web.dto;

/**
 * Body of {@code /like-post} and {@code /dislike-post}.
 *
 * <p>Quirk: the Node client sends the raw JWT in the {@code userId} field
 * (not the userId itself). engagement-service decodes it to the actual id.
 */
public record LikeRequest(String userId, String BlogId) {
}
