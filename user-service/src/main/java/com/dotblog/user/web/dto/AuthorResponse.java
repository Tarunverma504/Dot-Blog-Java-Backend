package com.dotblog.user.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Matches Node response shape:
 * {@code { name, profilePhoto, coverPhoto, about, Published: [blogs], Draft: [] }}.
 *
 * <p>For non-owner views, the Node controller only fills {@code Published}
 * and leaves {@code Draft} empty (drafts are private). We mirror that here.
 */
public record AuthorResponse(
        String name,
        String profilePhoto,
        String coverPhoto,
        String about,
        @JsonProperty("Published") List<?> published,
        @JsonProperty("Draft") List<?> draft
) {
    public static AuthorResponse of(String name,
                                    String profilePhoto,
                                    String coverPhoto,
                                    String about,
                                    List<?> published) {
        return new AuthorResponse(
                name != null ? name : "",
                profilePhoto != null ? profilePhoto : "",
                coverPhoto != null ? coverPhoto : "",
                about != null ? about : "",
                published != null ? published : List.of(),
                List.of()
        );
    }
}
