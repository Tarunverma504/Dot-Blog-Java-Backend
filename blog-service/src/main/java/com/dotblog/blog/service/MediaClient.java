package com.dotblog.blog.service;

import com.dotblog.blog.config.MediaClientProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

/** Thin RestClient wrapper around media-service for thumbnail upload + cleanup. */
@Service
public class MediaClient {

    private final RestClient client;
    private final String thumbnailFolder;

    public MediaClient(MediaClientProperties props) {
        this.client = RestClient.builder().baseUrl(props.getBaseUrl()).build();
        this.thumbnailFolder = props.getThumbnailFolder();
    }

    public UploadResult uploadThumbnail(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thumbnail file is required");
        }
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
                }
            };
            body.add("file", resource);
            body.add("folder", thumbnailFolder);

            @SuppressWarnings("unchecked")
            Map<String, String> resp = client.post()
                    .uri("/internal/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "media-service returned empty response");
            }
            return new UploadResult(resp.getOrDefault("ImageUrl", ""), resp.getOrDefault("public_id", ""));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read thumbnail: " + e.getMessage());
        }
    }

    /** Best-effort delete. Swallows failures so callers can fire-and-forget. */
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            client.post()
                    .uri("/internal/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("public_id", publicId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // Cleanup is best-effort; we don't want UX failures because Cloudinary blipped.
        }
    }

    public record UploadResult(String url, String publicId) {}
}
