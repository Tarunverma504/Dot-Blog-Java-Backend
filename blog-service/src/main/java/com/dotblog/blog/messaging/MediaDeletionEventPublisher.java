package com.dotblog.blog.messaging;

import com.dotblog.blog.config.KafkaTopicsProperties;
import com.dotblog.events.MediaDeletionRequestedEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class MediaDeletionEventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(MediaDeletionEventPublisher.class);
    private final KafkaTemplate<String, MediaDeletionRequestedEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public MediaDeletionEventPublisher(
        KafkaTemplate<String, MediaDeletionRequestedEvent> kafkaTemplate,
        KafkaTopicsProperties topics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    } 

    public void publish(MediaDeletionRequestedEvent event){
        String topic = topics.getMediaDeletionRequested();
        String key = event.publicId();
        kafkaTemplate.send(topic, key, event).whenComplete((result, error)->{
            if(error != null){
                log.error("media deletion publish failed eventId={} publicId={} topic={}",
                        event.eventId(), key, topic, error);
                return;
            }

            RecordMetadata md = result.getRecordMetadata();
            log.info("media deletion publish ok eventId={} publicId={} topic={} partition={} offset={}",
                    event.eventId(), key, md.topic(), md.partition(), md.offset());
        });
    }

}
