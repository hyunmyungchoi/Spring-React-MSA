package com.springmsa.adminbff.session.redis;

import java.time.Instant;
import java.util.List;

public record MemberPresenceEventData(
        String streamId,
        String eventType,
        String sessionFingerprint,
        Long userId,
        String loginId,
        String username,
        List<String> roles,
        Instant occurredAt
) {
}
