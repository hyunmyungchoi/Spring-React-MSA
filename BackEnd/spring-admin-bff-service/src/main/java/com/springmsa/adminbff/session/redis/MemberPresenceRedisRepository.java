package com.springmsa.adminbff.session.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class MemberPresenceRedisRepository {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${admin-bff.member-presence.redis.namespace:spring:presence:member-bff}")
    private String presenceNamespace;

    @Value("${admin-bff.member-presence.stream.key:spring:presence:member-bff:events}")
    private String presenceStreamKey;

    @Value("${admin-bff.member-presence.stream.default-limit:50}")
    private int defaultEventLimit;

    public MemberSessionPresence findBySessionId(String sessionId) {
        String key = onlineSessionKey(sessionId);
        String lastHeartbeatValue = stringRedisTemplate.opsForValue().get(key);

        if (lastHeartbeatValue == null) {
            return MemberSessionPresence.offline();
        }

        Long ttlSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);

        if (ttlSeconds == null || ttlSeconds <= 0) {
            return MemberSessionPresence.offline();
        }

        Instant lastHeartbeatAt = parseInstant(lastHeartbeatValue);
        Instant onlineExpiresAt = Instant.now().plusSeconds(ttlSeconds);

        return new MemberSessionPresence(true, lastHeartbeatAt, onlineExpiresAt, ttlSeconds);
    }

    public List<MemberPresenceEventData> findRecentEvents() {
        int eventLimit = Math.max(defaultEventLimit, 1);
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .reverseRange(presenceStreamKey, Range.unbounded(), Limit.limit().count(eventLimit));

        if (records == null) {
            return List.of();
        }

        return records.stream()
                .map(this::toEventData)
                .toList();
    }

    private String onlineSessionKey(String sessionId) {
        return presenceNamespace + ":sessions:" + sessionId;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);

        } catch (RuntimeException e) {
            return null;
        }
    }

    private MemberPresenceEventData toEventData(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();

        return new MemberPresenceEventData(
                record.getId().getValue(),
                stringValue(value.get("eventType")),
                stringValue(value.get("sessionFingerprint")),
                longValue(value.get("userId")),
                stringValue(value.get("loginId")),
                stringValue(value.get("username")),
                rolesValue(value.get("roles")),
                parseInstant(stringValue(value.get("occurredAt")))
        );
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }

        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private Long longValue(Object value) {
        String stringValue = stringValue(value);

        if (stringValue == null) {
            return null;
        }

        try {
            return Long.parseLong(stringValue);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> rolesValue(Object value) {
        String roles = stringValue(value);

        if (roles == null) {
            return List.of();
        }

        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
    }
}
