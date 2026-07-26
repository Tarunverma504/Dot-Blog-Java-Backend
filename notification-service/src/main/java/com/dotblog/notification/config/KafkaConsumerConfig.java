package com.dotblog.notification.config;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler errorHandler(
        KafkaTemplate<Object, Object> kafkaTemplate,
        @Value("${dotblog.kafka.topics.otp-requested-dlt}") String dltTopic
    ){

        //After retries fail -> publish to DLT (same partition if possible)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> new TopicPartition(dltTopic, record.partition())
        );

        //3 retries, 2 seconds apart -> then DLT
        DefaultErrorHandler handler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(2000L, 3L)
        );

        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("retry attempt={} topic={} partition={} offset={} cause={}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(),
                        ex.getMessage())
        );

        return handler;
    }
    
}
