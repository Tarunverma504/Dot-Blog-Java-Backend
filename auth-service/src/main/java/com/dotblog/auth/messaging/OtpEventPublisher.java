package com.dotblog.auth.messaging;

import com.dotblog.auth.config.KafkaTopicsProperties;
import com.dotblog.events.SendOtpEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OtpEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OtpEventPublisher.class);
    private final KafkaTemplate<String, SendOtpEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    public OtpEventPublisher(KafkaTemplate<String, SendOtpEvent> kafkaTemplate, KafkaTopicsProperties kafkaTopicsProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = kafkaTopicsProperties;
    }

    public void publish(SendOtpEvent event) {
        String topic = topics.getOtpRequested();
        String key = event.userId();
        kafkaTemplate.send(topic, key, event)
                            .whenComplete((result, error)->{
                                if (error != null) {
                                    log.error("OTP publish failed eventId={} topic={} userId={}",
                                            event.eventId(), topic, event.userId(), error);
                                    return;
                                }
                                RecordMetadata md = result.getRecordMetadata();
                                log.info("OTP publish ok eventId={} topic={} partition={} offset={} userId={} purpose={}",
                                        event.eventId(), md.topic(), md.partition(), md.offset(),
                                        event.userId(), event.purpose());
                            });

    }

}
