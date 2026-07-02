package com.springmsa.memberbff.presence.redis;

import com.springmsa.memberbff.presence.MemberPresenceEventType;

import java.time.Instant;
import java.util.List;

public record MemberPresenceStreamEvent(
        MemberPresenceEventType eventType,
        String sessionFingerprint,
        Long userId,
        String loginId,
        String username,
        List<String> roles,
        Instant occurredAt
) {
}
