package com.dotblog.blog.web;

import com.dotblog.blog.service.BlogService;
import com.dotblog.blog.service.JwtSupport;
import com.dotblog.blog.service.MediaClient;
import com.dotblog.blog.web.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.dotblog.blog.messaging.MediaDeletionEventPublisher;
import com.dotblog.events.MediaDeletionRequestedEvent;
import java.util.UUID;
import java.time.Instant;


import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
public class BlogController {

    private final BlogService blogService;
    private final JwtSupport jwtSupport;
    private final MediaClient mediaClient;
    private final ObjectMapper objectMapper;
    private final MediaDeletionEventPublisher mediaDeletionEventPublisher;

    public BlogController(BlogService blogService,
                          JwtSupport jwtSupport,
                          MediaClient mediaClient,
                          ObjectMapper objectMapper,
                          MediaDeletionEventPublisher mediaDeletionEventPublisher) {
        this.blogService = blogService;
        this.jwtSupport = jwtSupport;
        this.mediaClient = mediaClient;
        this.objectMapper = objectMapper;
        this.mediaDeletionEventPublisher = mediaDeletionEventPublisher;
    }

    // ---- Day 4 ----

    @PostMapping("/create-blog-save")
    public ResponseEntity<Map<String, String>> createBlog(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateBlogRequest body
    ) {
        String userId = jwtSupport.requireUserIdFromHeader(authorization);
        blogService.createBlog(userId, body);
        return ResponseEntity.ok(Map.of("message", "Success"));
    }

    @GetMapping("/get-blog/{id}")
    public ResponseEntity<BlogDetailResponse> getBlog(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String optionalUserId = jwtSupport.tryUserIdFromHeader(authorization);
        return ResponseEntity.ok(blogService.getBlog(id, optionalUserId));
    }

    @PostMapping("/update-blog/{id}")
    public ResponseEntity<Map<String, String>> updateBlog(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateBlogRequest body
    ) {
        jwtSupport.requireUserIdFromHeader(authorization);
        blogService.updateBlog(id, body);
        return ResponseEntity.ok(Map.of("message", "Blog Saved Successfully"));
    }

    // ---- Day 5 ----

    @GetMapping("/get-user-blogs")
    public ResponseEntity<UserBlogsResponse> getUserBlogs(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String userId = jwtSupport.requireUserIdFromHeader(authorization);
        return ResponseEntity.ok(blogService.getUserBlogs(userId));
    }

    @GetMapping("/get-blogs")
    public ResponseEntity<List<BlogListItem>> getBlogs() {
        return ResponseEntity.ok(blogService.getAllBlogsNoSort());
    }

    @GetMapping("/get-all-blogs")
    public ResponseEntity<List<BlogListItem>> getAllPublished(
            @RequestParam(value = "search", required = false) String search
    ) {
        return ResponseEntity.ok(blogService.getAllPublished(search));
    }

    @GetMapping("/get-categories-blogs/{category}")
    public ResponseEntity<List<BlogListItem>> getCategoriesBlogs(@PathVariable String category) {
        return ResponseEntity.ok(blogService.getByCategory(category));
    }

    @PostMapping("/publish-blog")
    public ResponseEntity<Map<String, String>> publishBlog(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PublishBlogRequest body
    ) {
        jwtSupport.requireUserIdFromHeader(authorization);
        blogService.publishBlog(body != null ? body.blogId() : null);
        return ResponseEntity.ok(Map.of("message", "Blog Published Successfully"));
    }

    /**
     * Soft-delete a blog. Only the owner can call this; service layer returns
     * 403 for everyone else. Hidden blogs are filtered out of every other
     * read endpoint, so this is effectively a delete from the world's view.
     */
    @PostMapping("/delete-blog/{id}")
    public ResponseEntity<Map<String, String>> deleteBlog(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String userId = jwtSupport.requireUserIdFromHeader(authorization);
        blogService.deleteBlog(id, userId);
        return ResponseEntity.ok(Map.of("message", "Blog Deleted Successfully"));
    }

    /**
     * Matches Node: multipart with file field {@code thumbnail_Img} and a
     * JSON-string body field {@code PrevImage} (which the React client sends
     * via {@code JSON.stringify}). On success returns {@code {ImageUrl, public_id}}
     * and best-effort destroys the previous Cloudinary asset.
     */
    @PostMapping(value = "/upload-thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadThumbnail(
            @RequestPart("thumbnail_Img") MultipartFile file,
            @RequestPart(value = "PrevImage", required = false) String prevImageJson
    ) {
        MediaClient.UploadResult result = mediaClient.uploadThumbnail(file);
        String prevPublicId = extractPublicId(prevImageJson);
        if (prevPublicId != null && !prevPublicId.isBlank()) {

            mediaDeletionEventPublisher.publish(new MediaDeletionRequestedEvent(
                UUID.randomUUID().toString(),
                prevPublicId,
                "BLOG_THUMBNAIL_REPLACED",
                Instant.now()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "ImageUrl", result.url(),
                "public_id", result.publicId()
        ));
    }

    private String extractPublicId(String prevImageJson) {
        if (prevImageJson == null || prevImageJson.isBlank()) return null;
        try {
            Map<?, ?> map = objectMapper.readValue(prevImageJson, Map.class);
            Object pid = map.get("public_id");
            return pid != null ? pid.toString() : null;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PrevImage must be JSON");
        }
    }
}
