package com.springmsa.adminbff.session.dto;

import java.time.Instant;
import java.util.List;

public record MemberPresenceEventResponse(
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
