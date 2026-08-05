package com.springmsa.kafka.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MsaEventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        String producer,
        Instant occurredAt,
        T payload
) {

    public MsaEventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
    }

    public static <T> MsaEventEnvelope<T> create(
            String eventType,
            int eventVersion,
            String producer,
            Instant occurredAt,
            T payload
    ) {
        return new MsaEventEnvelope<>(
                UUID.randomUUID(), eventType, eventVersion, producer, occurredAt, payload
        );
    }
}
