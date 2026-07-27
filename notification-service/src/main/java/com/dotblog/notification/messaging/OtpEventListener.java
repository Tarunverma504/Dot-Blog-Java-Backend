package com.dotblog.notification.messaging;

import com.dotblog.events.DeliveryChannel;
import com.dotblog.events.SendOtpEvent;
import com.dotblog.notification.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import com.dotblog.notification.domain.ProcessedEvent;
import com.dotblog.notification.repository.ProcessedEventRepository;
import org.springframework.dao.DuplicateKeyException;

@Component
public class OtpEventListener {

    private static final Logger log = LoggerFactory.getLogger(OtpEventListener.class);

    private final EmailService emailService;
    private final ProcessedEventRepository processedEventRepository;


    public OtpEventListener(EmailService emailService, ProcessedEventRepository processedEventRepository) {
        this.emailService = emailService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(
            topics = "${dotblog.kafka.topics.otp-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOtpRequested(
            @Payload SendOtpEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info(
                "otp event received eventId={} userId={} channel={} purpose={} key={} partition={} offset={}",
                event.eventId(), event.userId(), event.channel(), event.purpose(),
                key, partition, offset);
        if(processedEventRepository.existsById(event.eventId())){
            log.info("duplicate event, skipping eventId={} partition={} offset={}", event.eventId(), partition, offset);
            return;
        }

        if (event.channel() != DeliveryChannel.EMAIL) {
            log.warn("skipping non-EMAIL channel eventId={} channel={}", event.eventId(), event.channel());
            return;
        }

        String purpose = event.purpose() == null ? "" : event.purpose();
        boolean ok = switch (purpose) {
            case "REGISTRATION", "RESEND", "SMOKE_TEST" ->
                    emailService.sendOtpText(event.recipient(), event.otp());
            case "FORGOT_PASSWORD" ->
                    emailService.sendForgotPasswordLink(event.recipient(), event.otp());
            default -> {
                log.warn("unknown purpose eventId={} purpose={} — skipping without retry",
                        event.eventId(), purpose);
                yield true; // treat as handled (skip), not failure
            }
        };

        if (!ok) {
            throw new IllegalStateException("email send failed for eventId=" + event.eventId());
        }

        try {
            processedEventRepository.insert(new ProcessedEvent(event.eventId(), purpose));
        } catch (DuplicateKeyException e) {
            log.warn("dedup insert race lost eventId={}", event.eventId());
        }

        log.info("otp event handled eventId={} purpose={}", event.eventId(), purpose);
    }
}
