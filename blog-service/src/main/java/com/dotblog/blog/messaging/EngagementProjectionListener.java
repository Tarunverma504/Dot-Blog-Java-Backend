package com.dotblog.blog.messaging;

import com.dotblog.blog.domain.Blog;
import com.dotblog.blog.domain.ProcessedEvent;
import com.dotblog.blog.repository.ProcessedEventRepository;
import com.dotblog.events.BlogCommentDeletedEvent;
import com.dotblog.events.BlogCommentedEvent;
import com.dotblog.events.BlogLikedEvent;
import com.dotblog.events.BlogUnlikedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EngagementProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(EngagementProjectionListener.class);

    private final MongoTemplate mongoTemplate;
    private final ProcessedEventRepository processedEventRepository;

    public EngagementProjectionListener(
            MongoTemplate mongoTemplate,
            ProcessedEventRepository processedEventRepository
    ) {
        this.mongoTemplate = mongoTemplate;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(
            topics = "${dotblog.kafka.topics.blog-liked}",
            groupId = "${dotblog.kafka.consumer.group-id}",
            properties = {
                    "spring.json.value.default.type:com.dotblog.events.BlogLikedEvent",
                    "spring.json.use.type.headers:false"
            }
    )
    public void listenBlogLiked(BlogLikedEvent event) {
        if (!claim(event.eventId(), "BLOG_LIKED")) {
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(event.blogId())),
                new Update().inc("likesCount", 1),
                Blog.class
        );
    }

    @KafkaListener(
            topics = "${dotblog.kafka.topics.blog-unliked}",
            groupId = "${dotblog.kafka.consumer.group-id}",
            properties = {
                    "spring.json.value.default.type:com.dotblog.events.BlogUnlikedEvent",
                    "spring.json.use.type.headers:false"
            }
    )
    public void listenBlogUnliked(BlogUnlikedEvent event) {
        if (!claim(event.eventId(), "BLOG_UNLIKED")) {
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(event.blogId()).and("likesCount").gt(0)),
                new Update().inc("likesCount", -1),
                Blog.class
        );
    }

    @KafkaListener(
            topics = "${dotblog.kafka.topics.blog-commented}",
            groupId = "${dotblog.kafka.consumer.group-id}",
            properties = {
                    "spring.json.value.default.type:com.dotblog.events.BlogCommentedEvent",
                    "spring.json.use.type.headers:false"
            }
    )
    public void listenBlogCommented(BlogCommentedEvent event) {
        if (!claim(event.eventId(), "BLOG_COMMENTED")) {
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(event.blogId())),
                new Update().inc("commentsCount", 1),
                Blog.class
        );
    }

    @KafkaListener(
            topics = "${dotblog.kafka.topics.blog-comment-deleted}",
            groupId = "${dotblog.kafka.consumer.group-id}",
            properties = {
                    "spring.json.value.default.type:com.dotblog.events.BlogCommentDeletedEvent",
                    "spring.json.use.type.headers:false"
            }
    )
    public void listenBlogCommentDeleted(BlogCommentDeletedEvent event) {
        if (!claim(event.eventId(), "BLOG_COMMENT_DELETED")) {
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(event.blogId()).and("commentsCount").gt(0)),
                new Update().inc("commentsCount", -1),
                Blog.class
        );
    }

    /**
     * Claim an eventId before applying a counter update. Duplicate deliveries
     * hit the unique _id and are skipped so likesCount/commentsCount don't drift.
     */
    private boolean claim(String eventId, String purpose) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("engagement projection missing eventId purpose={}", purpose);
            return false;
        }
        try {
            processedEventRepository.insert(new ProcessedEvent(eventId, purpose));
            return true;
        } catch (DuplicateKeyException e) {
            log.info("duplicate engagement event skipped eventId={} purpose={}", eventId, purpose);
            return false;
        }
    }
}
