package com.dotblog.media.messaging;

import com.dotblog.events.MediaDeletionRequestedEvent;
import com.dotblog.media.service.MediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class MediaDeletionEventListener {
    private static final Logger log = LoggerFactory.getLogger(MediaDeletionEventListener.class);

    private final MediaService mediaService;

    public MediaDeletionEventListener(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @RetryableTopic(
        attempts = "6",
        backoff = @Backoff(delay = 30_000, multiplier = 2.0),
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
        topics = "${dotblog.kafka.topics.media-deletion-requested}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onDeletionRequested(MediaDeletionRequestedEvent event) {
        log.info("media deletion event received eventId={} publicId={} reason={}",
                event.eventId(), event.publicId(), event.reason());
        if (!mediaService.delete(event.publicId())) {
            throw new IllegalStateException("cloudinary delete failed for " + event.publicId());
        }
        log.info("media deletion ok eventId={} publicId={}", event.eventId(), event.publicId());
    }
}
