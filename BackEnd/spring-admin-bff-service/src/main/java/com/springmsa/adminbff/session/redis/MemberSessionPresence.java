package com.springmsa.adminbff.session.redis;

import java.time.Instant;

public record MemberSessionPresence(
        boolean online,
        Instant lastHeartbeatAt,
        Instant onlineExpiresAt,
        Long onlineTtlSeconds
) {
    public static MemberSessionPresence offline() {
        return new MemberSessionPresence(false, null, null, null);
    }
}
