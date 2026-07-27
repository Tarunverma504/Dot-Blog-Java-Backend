package com.dotblog.auth.messaging;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import com.dotblog.events.UserVerifiedEvent;
import com.dotblog.auth.config.KafkaTopicsProperties;
import org.apache.kafka.clients.producer.RecordMetadata;

@Service
public class UserVerifiedEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(UserVerifiedEventPublisher.class);
    private final KafkaTemplate<String, UserVerifiedEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public UserVerifiedEventPublisher(KafkaTemplate<String, UserVerifiedEvent> kafkaTemplate, KafkaTopicsProperties kafkaTopicsProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = kafkaTopicsProperties;
    }

    public void publish(UserVerifiedEvent userVerifiedEvent){
        String topic = topics.getUserVerified();
        String key = userVerifiedEvent.userId();
        log.info("Publishing UserVerifiedEvent to topic={} userId={}", topic, key);
        kafkaTemplate.send(topic, key, userVerifiedEvent)
                        .whenComplete((result, error)->{
                            if (error != null) {
                                log.error("User verified publish failed userId={} topic={} error={}",
                                        key, topic, error);
                                return;
                            }
                            log.info("User verified publish ok userId={} topic={}", key, topic);
                            RecordMetadata md = result.getRecordMetadata();
                            log.info("User verified publish ok userId={} topic={} partition={} offset={}",
                                    key, topic, md.partition(), md.offset());
                        });
    }


}
