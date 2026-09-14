package com.dotblog.media.messaging;

import com.dotblog.events.MediaDeletionRequestedEvent;
import com.dotblog.media.service.InvalidPublicIdException;
import com.dotblog.media.service.MediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class MediaDeletionEventListener {
    private static final Logger log = LoggerFactory.getLogger(MediaDeletionEventListener.class);

    /** Cloudinary-ish public ids: no spaces; letters/digits start; path segments allowed. */
    private static final Pattern PUBLIC_ID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_./-]{2,}$");

    private final MediaService mediaService;

    public MediaDeletionEventListener(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @RetryableTopic(
            attempts = "6",
            backoff = @Backoff(delay = 30_000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            // Spring Kafka name for "notRetryOn" — poison goes straight to DLT
            exclude = { InvalidPublicIdException.class }
    )
    @KafkaListener(
            topics = "${dotblog.kafka.topics.media-deletion-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onDeletionRequested(MediaDeletionRequestedEvent event) {
        log.info("media deletion event received eventId={} publicId={} reason={}",
                event.eventId(), event.publicId(), event.reason());

        requireValidPublicId(event.publicId());

        if (!mediaService.delete(event.publicId())) {
            throw new IllegalStateException("cloudinary delete failed for " + event.publicId());
        }
        log.info("media deletion ok eventId={} publicId={}", event.eventId(), event.publicId());
    }

    @DltHandler
    public void onDlt(
            MediaDeletionRequestedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        log.error("media deletion DLT eventId={} publicId={} reason={} topic={}",
                event != null ? event.eventId() : null,
                event != null ? event.publicId() : null,
                event != null ? event.reason() : null,
                topic);
    }

    private static void requireValidPublicId(String publicId) {
        if (publicId == null || publicId.isBlank() || !PUBLIC_ID_PATTERN.matcher(publicId).matches()) {
            throw new InvalidPublicIdException(publicId);
        }
    }
}
