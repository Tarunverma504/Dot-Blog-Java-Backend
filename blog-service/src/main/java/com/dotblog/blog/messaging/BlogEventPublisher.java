package com.dotblog.blog.messaging;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import com.dotblog.events.BlogPublishedEvent;
import com.dotblog.blog.config.KafkaTopicsProperties;

@Service
public class BlogEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(BlogEventPublisher.class);
    private final KafkaTemplate<String, BlogPublishedEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    public BlogEventPublisher(KafkaTemplate<String, BlogPublishedEvent> kafkaTemplate, KafkaTopicsProperties kafkaTopicsProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = kafkaTopicsProperties;
    }

    public void publish(BlogPublishedEvent event){
        String topic = topics.getBlogPublished();
        String key = event.blogId();
        kafkaTemplate.send(topic, key, event).whenComplete((result, error)->{
            if (error != null) {
                logger.error("Blog published event failed eventId={} topic={} blogId={}",
                        event.eventId(), topic, key, error);
                return;
            }
            logger.info("Blog published event ok eventId={} topic={} blogId={} partition={} offset={}",
                    event.eventId(), topic, key,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        });
    }
}
