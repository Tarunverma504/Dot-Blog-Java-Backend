package com.dotblog.user.web;

import com.dotblog.user.service.JwtSupport;
import com.dotblog.user.service.ProfileService;
import com.dotblog.user.service.UserSummaryService;
import com.dotblog.user.web.dto.ProfilePayload;
import com.dotblog.user.web.dto.UpdateAboutRequest;
import com.dotblog.user.web.dto.UploadPhotoRequest;
import com.dotblog.user.web.dto.UploadPhotoResponse;
import com.dotblog.user.web.dto.UserSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ProfileController {

    private final ProfileService profileService;
    private final JwtSupport jwtSupport;
    private final UserSummaryService userSummaryService;

    public ProfileController(ProfileService profileService, JwtSupport jwtSupport, UserSummaryService userSummaryService) {
        this.profileService = profileService;
        this.jwtSupport = jwtSupport;
        this.userSummaryService = userSummaryService;
    }

    @PostMapping("/api/v2/update-about")
    public ResponseEntity<ProfilePayload> updateAbout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateAboutRequest body
    ) {
        String userId = jwtSupport.userIdFromHeader(authorization);
        ProfilePayload payload = profileService.updateAbout(userId, body != null ? body.about() : null);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/api/v2/upload/profile-photo")
    public ResponseEntity<UploadPhotoResponse> uploadProfilePhoto(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UploadPhotoRequest body
    ) {
        String userId = jwtSupport.userIdFromHeader(authorization);
        return ResponseEntity.ok(profileService.updateProfilePhoto(userId, body));
    }

    @PostMapping("/api/v2/upload/cover-photo")
    public ResponseEntity<UploadPhotoResponse> uploadCoverPhoto(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UploadPhotoRequest body
    ) {
        String userId = jwtSupport.userIdFromHeader(authorization);
        return ResponseEntity.ok(profileService.updateCoverPhoto(userId, body));
    }

    /**
     * Internal endpoint used by blog-service after a blog is created. Not
     * exposed through the public gateway (gateway only routes /api/v2/*).
     */
    @PostMapping("/internal/users/{id}/posts")
    public ResponseEntity<Void> appendPost(@PathVariable String id, @RequestBody AppendPostBody body) {
        profileService.appendPost(id, body != null ? body.blogId() : null);
        return ResponseEntity.noContent().build();
    }

    /**
     * Internal batch lookup used by blog-service and engagement-service to
     * populate {@code comments.userId} and {@code blog.userId} the same way
     * Node mongoose populate did. Returns a map keyed by userId.
     */
    @PostMapping("/internal/users/summary")
    public ResponseEntity<Map<String, UserSummary>> summaryBatch(@RequestBody SummaryBatchRequest body) {
        List<String> ids = body != null && body.ids() != null ? body.ids() : List.of();
        return ResponseEntity.ok(userSummaryService.summariesByIds(ids));
    }

    public record AppendPostBody(String blogId) {}

    public record SummaryBatchRequest(List<String> ids) {}
}
