package com.dotblog.blog.web.dto;

import java.util.List;

/**
 * Mirrors Node's {@code GetBlog} response shape exactly so the React client
 * does not need to change. {@code comments} is a list of populated map
 * objects (each containing {@code _id}, {@code userId{_id,name,email,profilePhoto}},
 * {@code text}, {@code createdAt}).
 */
public record BlogDetailResponse(
        String Title,
        String Body,
        String Thumbnail,
        String AuthorName,
        String AuthorId,
        String AuthorPhoto,
        String SubText,
        String CreatedAt,
        int likes,
        List<Object> comments,
        boolean isAlreadyLiked,
        String Category
) {
}
