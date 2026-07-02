package com.springmsa.membercommunityservice.api;

import java.time.Instant;

public record CommunityPostResponse(
        Long id,
        String title,
        String content,
        String author,
        Instant createdAt,
        Instant updatedAt
) {
}
