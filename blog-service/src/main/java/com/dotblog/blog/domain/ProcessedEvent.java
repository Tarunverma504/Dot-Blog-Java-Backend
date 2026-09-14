package com.dotblog.blog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "processed_events")
public class ProcessedEvent {

    @Id
    private String eventId;

    @Indexed
    private Instant processedAt;

    private String purpose;

    protected ProcessedEvent() {}

    public ProcessedEvent(String eventId, String purpose) {
        this.eventId = eventId;
        this.purpose = purpose;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
    public String getPurpose() { return purpose; }
}
