package com.springmsa.memberbff.presence.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MemberPresenceRedisRepository {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${bff.presence.redis.namespace:spring:presence:member-bff}")
    private String presenceNamespace;

    @Value("${bff.presence.stream.key:spring:presence:member-bff:events}")
    private String presenceStreamKey;

    @Value("${bff.presence.stream.max-length:1000}")
    private long presenceStreamMaxLength;

    public void saveOnlineSession(String sessionId, Instant heartbeatAt, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(
                onlineSessionKey(sessionId),
                heartbeatAt.toString(),
                Duration.ofSeconds(ttlSeconds)
        );
    }

    public void deleteOnlineSession(String sessionId) {
        stringRedisTemplate.delete(onlineSessionKey(sessionId));
    }

    public void appendEvent(MemberPresenceStreamEvent event) {
        stringRedisTemplate.opsForStream().add(presenceStreamKey, eventFields(event));

        if (presenceStreamMaxLength > 0) {
            stringRedisTemplate.opsForStream().trim(presenceStreamKey, presenceStreamMaxLength, true);
        }
    }

    private String onlineSessionKey(String sessionId) {
        return presenceNamespace + ":sessions:" + sessionId;
    }

    private Map<String, String> eventFields(MemberPresenceStreamEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventType", event.eventType().name());
        fields.put("sessionFingerprint", event.sessionFingerprint());
        putIfPresent(fields, "userId", event.userId());
        putIfPresent(fields, "loginId", event.loginId());
        putIfPresent(fields, "username", event.username());
        putIfPresent(fields, "roles", event.roles());
        fields.put("occurredAt", event.occurredAt().toString());
        return fields;
    }

    private void putIfPresent(Map<String, String> fields, String fieldName, Object value) {
        if (value == null) {
            return;
        }

        String stringValue = value instanceof List<?> values
                ? String.join(",", values.stream().map(String::valueOf).toList())
                : String.valueOf(value);

        if (StringUtils.hasText(stringValue)) {
            fields.put(fieldName, stringValue);
        }
    }
}
