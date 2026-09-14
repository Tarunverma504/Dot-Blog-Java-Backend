package com.dotblog.engagement.messaging;

import com.dotblog.engagement.config.KafkaTopicsProperties;
import com.dotblog.events.BlogCommentDeletedEvent;
import com.dotblog.events.BlogCommentedEvent;
import com.dotblog.events.BlogLikedEvent;
import com.dotblog.events.BlogUnlikedEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EngagementEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EngagementEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public EngagementEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicsProperties topics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    public void publishLiked(BlogLikedEvent event) {
        send(topics.getBlogLiked(), event.blogId(), event.eventId(), event);
    }

    public void publishUnliked(BlogUnlikedEvent event) {
        send(topics.getBlogUnliked(), event.blogId(), event.eventId(), event);
    }

    public void publishCommented(BlogCommentedEvent event) {
        send(topics.getBlogCommented(), event.blogId(), event.eventId(), event);
    }

    public void publishCommentDeleted(BlogCommentDeletedEvent event) {
        send(topics.getBlogCommentDeleted(), event.blogId(), event.eventId(), event);
    }

    private void send(String topic, String key, String eventId, Object event) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("engagement publish failed eventId={} topic={} blogId={}",
                        eventId, topic, key, error);
                return;
            }
            RecordMetadata md = result.getRecordMetadata();
            log.info("engagement publish ok eventId={} topic={} blogId={} partition={} offset={}",
                    eventId, topic, key, md.partition(), md.offset());
        });
    }
}
