package com.dotblog.engagement.service;

import com.dotblog.engagement.domain.Engagement;
import com.dotblog.events.BlogCommentDeletedEvent;
import com.dotblog.events.BlogCommentedEvent;
import com.dotblog.events.BlogLikedEvent;
import com.dotblog.events.BlogUnlikedEvent;
import com.dotblog.engagement.messaging.EngagementEventPublisher;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Source of truth for likes and comments is the {@code engagements} collection
 * ({@code _id} = blogId). The {@code blogs} collection is read only to confirm
 * the post exists — arrays on that document are no longer mutated.
 */
@Service
public class EngagementService {

    private static final String BLOGS = "blogs";
    private static final String ENGAGEMENTS = "engagements";

    private final MongoTemplate mongoTemplate;
    private final UserClient userClient;
    private final EngagementEventPublisher engagementEventPublisher;

    public EngagementService(
            MongoTemplate mongoTemplate,
            UserClient userClient,
            EngagementEventPublisher engagementEventPublisher
    ) {
        this.mongoTemplate = mongoTemplate;
        this.userClient = userClient;
        this.engagementEventPublisher = engagementEventPublisher;
    }

    /** Push like and return new likes count. Idempotent: same userId twice = 1 entry total. */
    public int likePost(String userId, String blogId) {
        requireBlog(blogId);
        Engagement existing = findEngagement(blogId);
        if (containsLike(existing, userId)) {
            return likesCount(existing);
        }
        Document like = new Document("userId", userId)
                .append("createdAt", Date.from(Instant.now()));
        mongoTemplate.upsert(byEngagementId(blogId), new Update().push("likes", like), ENGAGEMENTS);
        engagementEventPublisher.publishLiked(new BlogLikedEvent(
                UUID.randomUUID().toString(),
                blogId,
                userId,
                Instant.now()
        ));
        return likesCount(findEngagement(blogId));
    }

    public int dislikePost(String userId, String blogId) {
        requireBlog(blogId);
        Update update = new Update().pull("likes", new Document("userId", userIdMatchValues(userId)));
        var result = mongoTemplate.updateFirst(byEngagementId(blogId), update, ENGAGEMENTS);
        if (result.getModifiedCount() > 0) {
            engagementEventPublisher.publishUnliked(new BlogUnlikedEvent(
                    UUID.randomUUID().toString(),
                    blogId,
                    userId,
                    Instant.now()
            ));
        }
        return likesCount(findEngagement(blogId));
    }

    public List<Map<String, Object>> addComment(String userId, String blogId, String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment required");
        }
        requireBlog(blogId);
        ObjectId commentObjectId = new ObjectId();
        Document comment = new Document()
                .append("_id", commentObjectId)
                .append("userId", userId)
                .append("text", text)
                .append("createdAt", Date.from(Instant.now()));
        mongoTemplate.upsert(byEngagementId(blogId), new Update().push("comments", comment), ENGAGEMENTS);
        engagementEventPublisher.publishCommented(new BlogCommentedEvent(
                UUID.randomUUID().toString(),
                blogId,
                userId,
                text,
                commentObjectId.toHexString(),
                Instant.now()
        ));
        return populateAndSort(findEngagement(blogId));
    }

    public List<Map<String, Object>> deleteComment(String commentId, String blogId) {
        requireBlog(blogId);
        Engagement existing = findEngagement(blogId);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        ObjectId commentObjectId;
        try {
            commentObjectId = new ObjectId(commentId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        String commentUserId = commentUserId(existing, commentId);
        Update update = new Update().pull("comments", new Document("_id", commentObjectId));
        var result = mongoTemplate.updateFirst(byEngagementId(blogId), update, ENGAGEMENTS);
        if (result.getModifiedCount() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        engagementEventPublisher.publishCommentDeleted(new BlogCommentDeletedEvent(
                UUID.randomUUID().toString(),
                blogId,
                commentUserId,
                commentId,
                Instant.now()
        ));
        return populateAndSort(findEngagement(blogId));
    }

    // ---------------- helpers ----------------

    private Query byEngagementId(String blogId) {
        return Query.query(Criteria.where("_id").is(requireBlogId(blogId)));
    }

    private Query byBlogObjectId(String blogId) {
        try {
            return Query.query(Criteria.where("_id").is(new ObjectId(requireBlogId(blogId))));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found");
        }
    }

    private String requireBlogId(String blogId) {
        if (blogId == null || blogId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BlogId required");
        }
        return blogId;
    }

    /** Existence check only — does not read or write likes/comments on blogs. */
    private void requireBlog(String blogId) {
        Document found = mongoTemplate.findOne(byBlogObjectId(blogId), Document.class, BLOGS);
        if (found == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found");
        }
    }

    private Engagement findEngagement(String blogId) {
        return mongoTemplate.findById(blogId, Engagement.class, ENGAGEMENTS);
    }

    /**
     * Builds a {@code {$in: [stringId, ObjectId(stringId)]}} match document so a
     * single equality filter (used inside {@code $pull}) hits both Node-era
     * ObjectId-typed values and Java-era String-typed values for the same user.
     */
    private static Document userIdMatchValues(String userId) {
        List<Object> values = new ArrayList<>(2);
        values.add(userId);
        try {
            values.add(new ObjectId(userId));
        } catch (IllegalArgumentException ignored) {
            // userId isn't a 24-char hex; only the String form is possible
        }
        return new Document("$in", values);
    }

    private static boolean containsLike(Engagement engagement, String userId) {
        if (engagement == null || engagement.getLikes() == null) {
            return false;
        }
        return engagement.getLikes().stream()
                .anyMatch(l -> l.getUserId() != null && userId.equals(String.valueOf(l.getUserId())));
    }

    private static int likesCount(Engagement engagement) {
        if (engagement == null || engagement.getLikes() == null) {
            return 0;
        }
        return engagement.getLikes().size();
    }

    private static String commentUserId(Engagement engagement, String commentId) {
        if (engagement.getComments() == null) {
            return null;
        }
        return engagement.getComments().stream()
                .filter(c -> c.getId() != null && commentId.equals(c.getId()))
                .map(Engagement.Comment::getUserId)
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> populateAndSort(Engagement engagement) {
        List<Engagement.Comment> comments = engagement != null && engagement.getComments() != null
                ? engagement.getComments()
                : List.of();
        if (comments.isEmpty()) {
            return List.of();
        }

        Set<String> userIds = new HashSet<>();
        for (Engagement.Comment c : comments) {
            if (c.getUserId() != null) {
                userIds.add(c.getUserId());
            }
        }
        Map<String, UserClient.UserSummary> summaries = userClient.summariesByIds(userIds);

        List<Engagement.Comment> sorted = new ArrayList<>(comments);
        sorted.sort((a, b) -> {
            Instant da = a.getCreatedAt();
            Instant db = b.getCreatedAt();
            if (da == null && db == null) return 0;
            if (da == null) return -1;
            if (db == null) return 1;
            return da.compareTo(db);
        });

        List<Map<String, Object>> out = new ArrayList<>(sorted.size());
        for (Engagement.Comment c : sorted) {
            UserClient.UserSummary s = c.getUserId() != null ? summaries.get(c.getUserId()) : null;

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("_id", c.getUserId() != null ? c.getUserId() : "");
            user.put("name", s != null ? s.name() : "");
            user.put("email", s != null ? s.email() : "");
            user.put("profilePhoto", s != null ? s.profilePhoto() : "");

            Map<String, Object> comment = new LinkedHashMap<>();
            comment.put("_id", c.getId() != null ? c.getId() : "");
            comment.put("userId", user);
            comment.put("text", c.getText());
            comment.put("createdAt", c.getCreatedAt());
            out.add(comment);
        }
        return out;
    }
}
