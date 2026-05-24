package com.dotblog.blog.web;

import com.dotblog.blog.service.BlogService;
import com.dotblog.blog.web.dto.BlogListItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Internal-only endpoints (not routed through the gateway). Other backend
 * services hit these directly on the docker network.
 */
@RestController
@RequestMapping("/internal")
public class InternalBlogController {

    private final BlogService blogService;

    public InternalBlogController(BlogService blogService) {
        this.blogService = blogService;
    }

    /**
     * {@code GET /internal/users/{userId}/blogs?publishedOnly=true} — returns
     * the user's blogs split into Published / Draft. Used by user-service to
     * populate {@code /api/v2/Author/:id}. Drafts are hidden from non-owners
     * by passing {@code publishedOnly=true}.
     */
    @GetMapping("/users/{userId}/blogs")
    public ResponseEntity<Map<String, List<BlogListItem>>> blogsForUser(
            @PathVariable String userId,
            @RequestParam(value = "publishedOnly", defaultValue = "false") boolean publishedOnly
    ) {
        return ResponseEntity.ok(blogService.getBlogsForUser(userId, publishedOnly));
    }
}
