package com.dotblog.engagement.web;

import com.dotblog.engagement.service.EngagementService;
import com.dotblog.engagement.service.JwtSupport;
import com.dotblog.engagement.web.dto.CommentRequest;
import com.dotblog.engagement.web.dto.DeleteCommentRequest;
import com.dotblog.engagement.web.dto.LikeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
public class EngagementController {

    private final EngagementService engagementService;
    private final JwtSupport jwtSupport;

    public EngagementController(EngagementService engagementService, JwtSupport jwtSupport) {
        this.engagementService = engagementService;
        this.jwtSupport = jwtSupport;
    }

    @PostMapping("/like-post")
    public ResponseEntity<Integer> likePost(@RequestBody LikeRequest body) {
        validateLike(body);
        String userId = jwtSupport.userIdFromToken(body.userId());
        return ResponseEntity.ok(engagementService.likePost(userId, body.BlogId()));
    }

    @PostMapping("/dislike-post")
    public ResponseEntity<Integer> dislikePost(@RequestBody LikeRequest body) {
        validateLike(body);
        String userId = jwtSupport.userIdFromToken(body.userId());
        return ResponseEntity.ok(engagementService.dislikePost(userId, body.BlogId()));
    }

    /** Path matches the Node typo {@code add-commnet}. */
    @PostMapping("/add-commnet")
    public ResponseEntity<List<Map<String, Object>>> addComment(@RequestBody CommentRequest body) {
        if (body == null || body.BlogId() == null || body.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BlogId and userId required");
        }
        String userId = jwtSupport.userIdFromToken(body.userId());
        return ResponseEntity.ok(engagementService.addComment(userId, body.BlogId(), body.Comment()));
    }

    @PostMapping("/delete-comment")
    public ResponseEntity<List<Map<String, Object>>> deleteComment(@RequestBody DeleteCommentRequest body) {
        if (body == null || body.blogId() == null || body.commentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "blogId and commentId required");
        }
        return ResponseEntity.ok(engagementService.deleteComment(body.commentId(), body.blogId()));
    }

    private void validateLike(LikeRequest body) {
        if (body == null || body.BlogId() == null || body.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BlogId and userId required");
        }
    }
}
