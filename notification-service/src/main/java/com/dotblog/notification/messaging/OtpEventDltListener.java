package com.dotblog.notification.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class OtpEventDltListener {
    private static final Logger log = LoggerFactory.getLogger(OtpEventDltListener.class);

    @KafkaListener(
        topics = "${dotblog.kafka.topics.otp-requested-dlt}",
            groupId = "notification-service-dlt"   // DIFFERENT group — ops/monitor only
    )
    public void onDeadLetter(
        String payload,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage
    ){
        // Ops alert hook — never re-process here automatically
        log.error("DLT message topic={} partition={} offset={} exception={} payloadSnippet={}",
                topic, partition, offset, exceptionMessage,
                payload == null ? "null" : payload.substring(0, Math.min(200, payload.length())));
    }



}
