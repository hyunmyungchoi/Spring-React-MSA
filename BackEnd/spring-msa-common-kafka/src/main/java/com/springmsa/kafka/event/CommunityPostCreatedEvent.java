package com.springmsa.kafka.event;

import java.time.Instant;

public record CommunityPostCreatedEvent(
        Long postId,
        String ownerSub,
        String author,
        String title,
        Instant createdAt
) {
}
