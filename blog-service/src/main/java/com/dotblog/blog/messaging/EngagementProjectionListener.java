package com.dotblog.blog.messaging;

import com.dotblog.blog.domain.Blog;
import com.dotblog.events.BlogCommentDeletedEvent;
import com.dotblog.events.BlogCommentedEvent;
import com.dotblog.events.BlogLikedEvent;
import com.dotblog.events.BlogUnlikedEvent;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EngagementProjectionListener {

    private final MongoTemplate mongoTemplate;

    public EngagementProjectionListener(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
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
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(event.blogId()).and("commentsCount").gt(0)),
                new Update().inc("commentsCount", -1),
                Blog.class
        );
    }
}
