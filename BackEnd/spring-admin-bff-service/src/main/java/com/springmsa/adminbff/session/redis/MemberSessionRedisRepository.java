package com.springmsa.adminbff.session.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberSessionRedisRepository {

    private static final String CREATION_TIME = "creationTime";
    private static final String LAST_ACCESSED_TIME = "lastAccessedTime";
    private static final String MAX_INACTIVE_INTERVAL = "maxInactiveInterval";
    private static final String SESSION_ATTR_PREFIX = "sessionAttr:";

    private final RedisConnectionFactory redisConnectionFactory;
    private final StringRedisSerializer stringSerializer = new StringRedisSerializer();
    private final JdkSerializationRedisSerializer valueSerializer = new JdkSerializationRedisSerializer();

    @Value("${admin-bff.member-session.redis.namespace:spring:session:bff}")
    private String memberSessionNamespace;

    public List<MemberSessionRedisData> findMemberSessions() {
        String sessionKeyPrefix = memberSessionNamespace + ":sessions:";
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(sessionKeyPrefix + "*")
                .count(500)
                .build();

        try (
                RedisConnection connection = redisConnectionFactory.getConnection();
                Cursor<byte[]> cursor = connection.keyCommands().scan(scanOptions)
        ) {
            return cursor.stream()
                    .map(this::deserializeKey)
                    .filter(key -> isSessionDataKey(key, sessionKeyPrefix))
                    .map(key -> readSession(connection, key, sessionKeyPrefix))
                    .flatMap(Optional::stream)
                    .toList();
        }
    }

    private Optional<MemberSessionRedisData> readSession(RedisConnection connection, String key, String sessionKeyPrefix) {
        Map<byte[], byte[]> entries = connection.hashCommands().hGetAll(serializeKey(key));

        if (entries.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new MemberSessionRedisData(
                key.substring(sessionKeyPrefix.length()),
                longValue(attribute(entries, "userId")),
                stringValue(attribute(entries, "loginId")),
                stringValue(attribute(entries, "name")),
                coalesceString(attribute(entries, "username"), attribute(entries, "name")),
                stringValue(attribute(entries, "email")),
                rolesValue(attribute(entries, "roles")),
                instantValue(field(entries, CREATION_TIME)),
                instantValue(field(entries, LAST_ACCESSED_TIME)),
                durationSeconds(field(entries, MAX_INACTIVE_INTERVAL))
        ));
    }

    private boolean isSessionDataKey(String key, String sessionKeyPrefix) {
        if (!key.startsWith(sessionKeyPrefix)) {
            return false;
        }

        String sessionId = key.substring(sessionKeyPrefix.length());
        return !sessionId.isBlank()
                && !sessionId.startsWith("expires:")
                && !sessionId.contains(":");
    }

    private Object field(Map<byte[], byte[]> entries, String fieldName) {
        return entries.entrySet().stream()
                .filter(entry -> fieldName.equals(deserializeHashKey(entry.getKey())))
                .map(entry -> deserializeValue(entry.getValue()))
                .findFirst()
                .orElse(null);
    }

    private Object attribute(Map<byte[], byte[]> entries, String attributeName) {
        return field(entries, SESSION_ATTR_PREFIX + attributeName);
    }

    private byte[] serializeKey(String key) {
        return Objects.requireNonNull(stringSerializer.serialize(key));
    }

    private String deserializeKey(byte[] key) {
        String value = stringSerializer.deserialize(key);
        return value == null ? "" : value;
    }

    private String deserializeHashKey(byte[] hashKey) {
        String value = stringSerializer.deserialize(hashKey);
        return value == null ? "" : value;
    }

    private Object deserializeValue(byte[] value) {
        if (value == null) {
            return null;
        }

        try {
            return valueSerializer.deserialize(value);

        } catch (SerializationException e) {
            return new String(value, StandardCharsets.UTF_8);
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }

        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private String coalesceString(Object... values) {
        for (Object value : values) {
            String stringValue = stringValue(value);

            if (stringValue != null) {
                return stringValue;
            }
        }

        return null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

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

    private Instant instantValue(Object value) {
        Long epochMillis = longValue(value);
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }

    private long durationSeconds(Object value) {
        if (value instanceof Duration duration) {
            return duration.getSeconds();
        }

        Long seconds = longValue(value);
        return seconds == null ? 0 : seconds;
    }

    private List<String> rolesValue(Object value) {
        if (value instanceof Collection<?> roles) {
            return roles.stream()
                    .map(String::valueOf)
                    .filter(role -> !role.isBlank())
                    .toList();
        }

        String role = stringValue(value);

        if (role == null) {
            return List.of();
        }

        return List.of(role);
    }
}
