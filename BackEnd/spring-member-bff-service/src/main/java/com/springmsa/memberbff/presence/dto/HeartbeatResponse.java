package com.springmsa.memberbff.presence.dto;

import java.time.Instant;

public record HeartbeatResponse(
        boolean online,
        Instant heartbeatAt,
        Instant expiresAt,
        long ttlSeconds
) {
}
