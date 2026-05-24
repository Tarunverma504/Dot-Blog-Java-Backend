package com.dotblog.user.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Same shape as Node {@code GetUserBlogs}/{@code UpdateAbout}:
 * {@code { name, profilePhoto, coverPhoto, about, Published: [], Draft: [] }}.
 *
 * <p>Day 4 leaves Published/Draft empty; Day 5 will populate them by calling
 * blog-service.
 */
public record ProfilePayload(
        String name,
        String profilePhoto,
        String coverPhoto,
        String about,
        @JsonProperty("Published") List<Object> published,
        @JsonProperty("Draft") List<Object> draft
) {
    public static ProfilePayload of(String name, String profilePhoto, String coverPhoto, String about) {
        return new ProfilePayload(
                name != null ? name : "",
                profilePhoto != null ? profilePhoto : "",
                coverPhoto != null ? coverPhoto : "",
                about != null ? about : "",
                List.of(),
                List.of()
        );
    }
}
