package com.springmsa.kafka.event;

import java.time.Instant;

public record WatchlistItemAddedEvent(
        Long watchItemId,
        String ownerSub,
        String symbol,
        String memo,
        Instant createdAt
) {
}
