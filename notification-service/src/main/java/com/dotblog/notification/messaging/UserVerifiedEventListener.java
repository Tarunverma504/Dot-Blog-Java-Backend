package com.dotblog.notification.messaging;

import com.dotblog.events.UserVerifiedEvent;
import com.dotblog.notification.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class UserVerifiedEventListener {
    private static final Logger log = LoggerFactory.getLogger(UserVerifiedEventListener.class);
    private final EmailService emailService; 

    public UserVerifiedEventListener(EmailService emailService){
        this.emailService = emailService;
    }

    @KafkaListener(
        topics = "${dotblog.kafka.topics.user-verified}",
        groupId = "notification-service",
        properties = {
            "spring.json.value.default.type:com.dotblog.events.UserVerifiedEvent",
            "spring.json.use.type.headers:false"
        }
    )
    public void onUserVerified(
        @Payload UserVerifiedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ){
        log.info("user verified event received userId={} partition={} offset={}",
                event.userId(), partition, offset);
        boolean ok = emailService.sendWelcome(event.email(), event.name());
        if (!ok) {
            throw new IllegalStateException("welcome email failed for userId=" + event.userId());
        }
        log.info("welcome email handled userId={}", event.userId());
    }
}
