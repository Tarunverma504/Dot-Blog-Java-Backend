package com.dotblog.media.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.dotblog.media.config.CloudinaryProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Owns all Cloudinary calls for the platform. Other services delegate via
 * {@code MediaClient}; the credentials only live here.
 */
@Service
public class MediaService {

    private final Cloudinary cloudinary;

    public MediaService(CloudinaryProperties props) {
        if (props.isConfigured()) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", props.getCloudName(),
                    "api_key", props.getApiKey(),
                    "api_secret", props.getApiSecret(),
                    "secure", true
            ));
        } else {
            this.cloudinary = null;
        }
    }

    public UploadResult upload(MultipartFile file, String folder) {
        requireConfigured();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        Path tmp = null;
        try {
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
            String suffix = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
            tmp = Files.createTempFile("dotblog-media-", suffix);
            file.transferTo(tmp.toFile());
            Map<String, Object> uploadOpts = new java.util.HashMap<>();
            if (folder != null && !folder.isBlank()) {
                uploadOpts.put("folder", folder);
            }
            uploadOpts.put("crop", "scale");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(tmp.toFile(), uploadOpts);
            return new UploadResult(stringOf(result, "url"), stringOf(result, "public_id"));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed: " + e.getMessage());
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
    }

    /** Best-effort delete. Returns {@code true} if Cloudinary reports success. */
    public boolean delete(String publicId) {
        if (cloudinary == null || publicId == null || publicId.isBlank()) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> res = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            Object outcome = res != null ? res.get("result") : null;
            return outcome != null && "ok".equals(outcome.toString());
        } catch (Exception e) {
            return false;
        }
    }

    private void requireConfigured() {
        if (cloudinary == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudinary is not configured (set CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET on media-service)");
        }
    }

    private static String stringOf(Map<String, Object> m, String key) {
        Object v = m != null ? m.get(key) : null;
        return v != null ? v.toString() : "";
    }

    public record UploadResult(String url, String publicId) {}
}
