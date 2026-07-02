package com.springmsa.adminbff.session.redis;

import java.time.Instant;
import java.util.List;

public record MemberSessionRedisData(
        String sessionId,
        Long userId,
        String loginId,
        String name,
        String username,
        String email,
        List<String> roles,
        Instant createdAt,
        Instant lastAccessedAt,
        long maxInactiveIntervalSeconds
) {
}
