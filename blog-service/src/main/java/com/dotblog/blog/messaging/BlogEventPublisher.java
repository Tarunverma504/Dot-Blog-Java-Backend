package com.dotblog.blog.messaging;

import com.dotblog.blog.config.KafkaTopicsProperties;
import com.dotblog.events.BlogCreatedEvent;
import com.dotblog.events.BlogPublishedEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class BlogEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(BlogEventPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public BlogEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicsProperties kafkaTopicsProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = kafkaTopicsProperties;
    }

    public void publishCreated(BlogCreatedEvent event) {
        send(topics.getBlogCreated(), event.blogId(), event.eventId(), event);
    }

    /** Used by the outbox relay so a row is marked PUBLISHED only after the broker acks. */
    public void publishCreatedAndWait(BlogCreatedEvent event) {
        String topic = topics.getBlogCreated();
        try {
            kafkaTemplate.send(topic, event.blogId(), event).get(15, TimeUnit.SECONDS);
            logger.info("blog created event acked eventId={} topic={} blogId={}",
                    event.eventId(), topic, event.blogId());
        } catch (Exception e) {
            throw new IllegalStateException("blog created event publish failed eventId=" + event.eventId(), e);
        }
    }

    public void publish(BlogPublishedEvent event) {
        send(topics.getBlogPublished(), event.blogId(), event.eventId(), event);
    }

    private void send(String topic, String key, String eventId, Object event) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, error) -> {
            if (error != null) {
                logger.error("blog event publish failed eventId={} topic={} blogId={}",
                        eventId, topic, key, error);
                return;
            }
            RecordMetadata md = result.getRecordMetadata();
            logger.info("blog event publish ok eventId={} topic={} blogId={} partition={} offset={}",
                    eventId, topic, key, md.partition(), md.offset());
        });
    }
}
