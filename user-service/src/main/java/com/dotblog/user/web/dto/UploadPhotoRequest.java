package com.dotblog.user.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Matches Node body for {@code /upload/profile-photo} and {@code /upload/cover-photo}. */
public record UploadPhotoRequest(
        String url,
        @JsonProperty("public_id") String publicId
) {
}
