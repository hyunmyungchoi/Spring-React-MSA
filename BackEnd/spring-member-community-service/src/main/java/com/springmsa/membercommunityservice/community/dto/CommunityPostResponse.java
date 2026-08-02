package com.springmsa.membercommunityservice.community.dto;

import java.time.Instant;

public record CommunityPostResponse(
        Long id,
        String title,
        String content,
        String author,
        boolean ownedByCurrentUser,
        Instant createdAt,
        Instant updatedAt
) {
}
