package com.dotblog.user.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Matches Node response: { ImageUrl, public_id }. */
public record UploadPhotoResponse(
        @JsonProperty("ImageUrl") String imageUrl,
        @JsonProperty("public_id") String publicId
) {
    public static UploadPhotoResponse of(String url, String publicId) {
        return new UploadPhotoResponse(
                url != null ? url : "",
                publicId != null ? publicId : ""
        );
    }
}
