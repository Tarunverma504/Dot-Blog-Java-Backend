package com.dotblog.blog.messaging;

import com.dotblog.blog.config.OutboxProperties;
import com.dotblog.blog.domain.OutboxEvent;
import com.dotblog.blog.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 6.3 — polls {@code outbox_events} and publishes PENDING rows to Kafka.
 * Claiming PENDING → PUBLISHING stops two overlapping ticks from double-sending.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final MongoTemplate mongoTemplate;
    private final BlogEventPublisher blogEventPublisher;
    private final OutboxProperties properties;

    public OutboxRelay(
            OutboxEventRepository outboxEventRepository,
            MongoTemplate mongoTemplate,
            BlogEventPublisher blogEventPublisher,
            OutboxProperties properties
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.mongoTemplate = mongoTemplate;
        this.blogEventPublisher = blogEventPublisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${dotblog.outbox.poll-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.STATUS_PENDING,
                PageRequest.of(0, properties.getBatchSize())
        );
        for (OutboxEvent row : batch) {
            OutboxEvent claimed = claim(row.getId());
            if (claimed == null) {
                continue;
            }
            try {
                blogEventPublisher.publishCreatedAndWait(claimed.getPayload());
                claimed.markPublished();
                outboxEventRepository.save(claimed);
                log.info("outbox published eventId={} blogId={}", claimed.getId(), claimed.getAggregateId());
            } catch (Exception e) {
                log.error("outbox publish failed eventId={} — will retry", claimed.getId(), e);
                claimed.markPending();
                outboxEventRepository.save(claimed);
            }
        }
    }

    private OutboxEvent claim(String eventId) {
        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(eventId).and("status").is(OutboxEvent.STATUS_PENDING)),
                new Update().set("status", OutboxEvent.STATUS_PUBLISHING),
                FindAndModifyOptions.options().returnNew(true),
                OutboxEvent.class
        );
    }
}
