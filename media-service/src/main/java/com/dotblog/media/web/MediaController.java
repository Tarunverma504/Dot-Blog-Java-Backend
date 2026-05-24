package com.dotblog.media.web;

import com.dotblog.media.service.MediaService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Internal API — not exposed through the public gateway. Other backend
 * services (blog-service, user-service) call these directly over the
 * docker network.
 */
@RestController
@RequestMapping("/internal")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /**
     * Upload a file to Cloudinary. {@code folder} selects the target
     * directory (e.g. {@code Dot-Blog/Blog_Thumbnails}).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "folder", required = false) String folder
    ) {
        MediaService.UploadResult r = mediaService.upload(file, folder);
        return ResponseEntity.ok(Map.of(
                "ImageUrl", r.url(),
                "public_id", r.publicId()
        ));
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestBody DeleteRequest body) {
        String id = body != null ? body.public_id() : null;
        boolean ok = mediaService.delete(id);
        return ResponseEntity.ok(Map.of("deleted", ok));
    }

    public record DeleteRequest(String public_id) {}
}
