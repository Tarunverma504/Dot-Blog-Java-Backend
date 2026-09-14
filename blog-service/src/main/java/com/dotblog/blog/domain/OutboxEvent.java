package com.dotblog.blog.domain;

import com.dotblog.events.BlogCreatedEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "outbox_events")
public class OutboxEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHING = "PUBLISHING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    private String id;

    private String topic;
    private String aggregateId;
    private String eventType;
    private BlogCreatedEvent payload;

    @Indexed
    private String status;
    private Instant createdAt;
    private Instant publishedAt;

    protected OutboxEvent() {}

    public static OutboxEvent pendingCreated(String topic, BlogCreatedEvent payload) {
        OutboxEvent row = new OutboxEvent();
        row.id = payload.eventId();
        row.topic = topic;
        row.aggregateId = payload.blogId();
        row.eventType = BlogCreatedEvent.class.getSimpleName();
        row.payload = payload;
        row.status = STATUS_PENDING;
        row.createdAt = Instant.now();
        return row;
    }

    public String getId() { return id; }
    public String getTopic() { return topic; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public BlogCreatedEvent getPayload() { return payload; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void markPublished() {
        this.status = STATUS_PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markPending() {
        this.status = STATUS_PENDING;
    }
}
