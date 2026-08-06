package com.dotblog.engagement.service;

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
import com.dotblog.engagement.messaging.EngagementEventPublisher;
import java.util.UUID;
import com.dotblog.events.BlogLikedEvent;
import com.dotblog.events.BlogUnlikedEvent;
import com.dotblog.events.BlogCommentedEvent;
import com.dotblog.events.BlogCommentDeletedEvent;
/**
 * Direct read/write to the shared {@code blogs} collection (same Atlas DB
 * blog-service uses). Untyped {@code Document} operations are deliberate —
 * engagement-service must not pull in blog-service's domain class.
 */
@Service
public class EngagementService {

    private static final String BLOGS = "blogs";

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
        Document blog = requireBlog(blogId);
        if (containsLike(blog, userId)) {
            return likesCount(blog);
        }
        Document like = new Document("userId", userId);
        Update update = new Update().push("likes", like);
        mongoTemplate.updateFirst(byId(blogId), update, BLOGS);
        engagementEventPublisher.publishLiked(new BlogLikedEvent(
            UUID.randomUUID().toString(),
            blogId,
            userId,
            Instant.now()
        ));
        return likesCount(requireBlog(blogId));
    }

    /**
     * Pull like (by userId in any like sub-doc).
     *
     * <p>Atlas has like.userId stored as both ObjectId (Node-era) and String
     * (Java-era). A naive {@code pull({userId: "..."})} would silently miss
     * the ObjectId-typed entries, so we match either representation via
     * {@code $in}.
     */
    public int dislikePost(String userId, String blogId) {
        requireBlog(blogId);
        Update update = new Update().pull("likes", new Document("userId", userIdMatchValues(userId)));
        var result = mongoTemplate.updateFirst(byId(blogId), update, BLOGS);
        if (result.getModifiedCount() > 0) {
            engagementEventPublisher.publishUnliked(new BlogUnlikedEvent(
                UUID.randomUUID().toString(),
                blogId,
                userId,
                Instant.now()
            ));
        }
        return likesCount(requireBlog(blogId));
    }

    public List<Map<String, Object>> addComment(String userId, String blogId, String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment required");
        }
        requireBlog(blogId);
        Document comment = new Document()
                .append("_id", new ObjectId())
                .append("userId", userId)
                .append("text", text)
                .append("createdAt", Date.from(Instant.now()));
        Update update = new Update().push("comments", comment);
        mongoTemplate.updateFirst(byId(blogId), update, BLOGS);
        engagementEventPublisher.publishCommented(new BlogCommentedEvent(
            UUID.randomUUID().toString(),
            blogId,
            userId,
            text,
            comment.getObjectId("_id").toString(),
            Instant.now()

        ));
        return populateAndSort(requireBlog(blogId));
    }

    public List<Map<String, Object>> deleteComment(String commentId, String blogId) {
        Document blog = requireBlog(blogId);
        if (blog == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found");
        }
        ObjectId commentObjectId;
        try {
            commentObjectId = new ObjectId(commentId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        Update update = new Update().pull("comments", new Document("_id", commentObjectId));
        var result = mongoTemplate.updateFirst(byId(blogId), update, BLOGS);
        if (result.getModifiedCount() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        else{
            engagementEventPublisher.publishCommentDeleted(new BlogCommentDeletedEvent(
                UUID.randomUUID().toString(),
                blogId,
                null,          // userId not available in this method — OK for now
                commentId,
                Instant.now()
            ));
        }

        return populateAndSort(requireBlog(blogId));
    }

    // ---------------- helpers ----------------

    private Query byId(String blogId) {
        if (blogId == null || blogId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BlogId required");
        }
        try {
            return Query.query(Criteria.where("_id").is(new ObjectId(blogId)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found");
        }
    }

    private Document requireBlog(String blogId) {
        Document found = mongoTemplate.findOne(byId(blogId), Document.class, BLOGS);
        if (found == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found");
        }
        return found;
    }

    /**
     * Builds a {@code {$in: [stringId, ObjectId(stringId)]}} match document so a
     * single equality filter (used inside {@code $pull}) hits both Node-era
     * ObjectId-typed values and Java-era String-typed values for the same user.
     * Falls back to a plain {@code {$in: [stringId]}} if the id isn't a valid
     * 24-char hex (defensive; shouldn't happen for real Mongo ids).
     */
    private static Document userIdMatchValues(String userId) {
        List<Object> values = new ArrayList<>(2);
        values.add(userId);
        try {
            values.add(new ObjectId(userId));
        } catch (IllegalArgumentException notAHexId) {
            // userId isn't a 24-char hex; only the String form is possible
        }
        return new Document("$in", values);
    }

    @SuppressWarnings("unchecked")
    private boolean containsLike(Document blog, String userId) {
        List<Document> likes = (List<Document>) blog.get("likes");
        if (likes == null) return false;
        return likes.stream().anyMatch(l -> userId.equals(String.valueOf(l.get("userId"))));
    }

    @SuppressWarnings("unchecked")
    private int likesCount(Document blog) {
        List<Document> likes = (List<Document>) blog.get("likes");
        return likes != null ? likes.size() : 0;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> populateAndSort(Document blog) {
        List<Document> comments = (List<Document>) blog.get("comments");
        if (comments == null || comments.isEmpty()) return List.of();

        Set<String> userIds = new HashSet<>();
        for (Document c : comments) {
            Object uid = c.get("userId");
            if (uid != null) userIds.add(uid.toString());
        }
        Map<String, UserClient.UserSummary> summaries = userClient.summariesByIds(userIds);

        List<Document> sorted = new ArrayList<>(comments);
        sorted.sort((a, b) -> {
            Object da = a.get("createdAt");
            Object db = b.get("createdAt");
            if (da == null && db == null) return 0;
            if (da == null) return -1;
            if (db == null) return 1;
            return ((Date) da).compareTo((Date) db);
        });

        List<Map<String, Object>> out = new ArrayList<>(sorted.size());
        for (Document c : sorted) {
            Object cid = c.get("_id");
            Object uid = c.get("userId");
            UserClient.UserSummary s = uid != null ? summaries.get(uid.toString()) : null;

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("_id", uid != null ? uid.toString() : "");
            user.put("name", s != null ? s.name() : "");
            user.put("email", s != null ? s.email() : "");
            user.put("profilePhoto", s != null ? s.profilePhoto() : "");

            Map<String, Object> comment = new LinkedHashMap<>();
            comment.put("_id", cid != null ? cid.toString() : "");
            comment.put("userId", user);
            comment.put("text", c.getString("text"));
            comment.put("createdAt", c.get("createdAt"));
            out.add(comment);
        }
        return out;
    }
}
