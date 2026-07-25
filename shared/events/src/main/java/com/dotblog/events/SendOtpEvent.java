package com.dotblog.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Event published when a user needs to receive a one-time password.
 *
 * Partition key convention: userId.
 *
 * Notes:
 *  - `otp` is secret and MUST NOT be logged.
 *  - `channel` allows the same event schema to support EMAIL / SMS / PUSH.
 *  - `purpose` distinguishes registration OTP from resend / forgot-password flows.
 *  - `eventId` is a UUID used for correlation across services and consumer-side deduplication.
 *  - Additive schema evolution only: never remove or rename fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SendOtpEvent(
        String eventId,
        Instant occurredAt,
        String userId,
        String recipient,
        DeliveryChannel channel,
        String otp,
        String purpose
) {
    @JsonCreator
    public SendOtpEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("userId") String userId,
            @JsonProperty("recipient") String recipient,
            @JsonProperty("channel") DeliveryChannel channel,
            @JsonProperty("otp") String otp,
            @JsonProperty("purpose") String purpose
    ) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.recipient = recipient;
        this.channel = channel;
        this.otp = otp;
        this.purpose = purpose;
    }
}
