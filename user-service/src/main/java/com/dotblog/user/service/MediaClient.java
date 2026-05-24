package com.dotblog.user.service;

import com.dotblog.user.config.MediaClientProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Best-effort RestClient wrapper around media-service for asset cleanup. */
@Service
public class MediaClient {

    private final RestClient client;

    public MediaClient(MediaClientProperties props) {
        this.client = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    /** Fire-and-forget delete. Failures are swallowed so user updates never block on Cloudinary. */
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
            // Best-effort cleanup.
        }
    }
}
