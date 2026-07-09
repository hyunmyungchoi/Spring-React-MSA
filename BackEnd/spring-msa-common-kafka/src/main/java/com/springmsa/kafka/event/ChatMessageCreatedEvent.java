package com.springmsa.kafka.event;

import java.time.Instant;

public record ChatMessageCreatedEvent(
        String eventId,
        Long messageId,
        String roomId,
        Long senderUserId,
        String senderLoginId,
        String senderName,
        String content,
        Instant sentAt,
        Instant occurredAt
) {
}
