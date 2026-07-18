package com.springmsa.adminbff.session.dto;

import java.time.Instant;
import java.util.List;

public record MemberSessionResponse(
        String sessionFingerprint,
        Long userId,
        String loginId,
        String name,
        String username,
        String email,
        List<String> roles,
        Instant createdAt,
        Instant lastAccessedAt,
        long maxInactiveIntervalSeconds,
        Instant expiresAt,
        boolean online,
        Instant lastHeartbeatAt,
        Instant onlineExpiresAt,
        Long onlineTtlSeconds
) {
}
