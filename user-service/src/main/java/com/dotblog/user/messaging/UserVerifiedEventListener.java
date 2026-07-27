package com.dotblog.user.messaging;

import com.dotblog.events.UserVerifiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class UserVerifiedEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserVerifiedEventListener.class);

    private final MongoTemplate mongoTemplate;

    public UserVerifiedEventListener(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @KafkaListener(
            topics = "${dotblog.kafka.topics.user-verified}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onUserVerified(
            @Payload UserVerifiedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("user verified event received userId={} partition={} offset={}",
                event.userId(), partition, offset);

        Query q = Query.query(Criteria.where("_id").is(event.userId()));
        Update update = new Update()
                .setOnInsert("about", "")
                .set("name", event.name())
                .set("email", event.email());

        mongoTemplate.upsert(q, update, "users");

        log.info("profile seeded userId={}", event.userId());
    }
}
