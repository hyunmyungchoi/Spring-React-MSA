package com.springmsa.memberbff.chat.cache;

import com.springmsa.memberbff.chat.dto.ChatMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class ChatRecentMessageCacheRepository {

    private static final String RECENT_MESSAGES_SUFFIX = "recent-messages";
    private static final String WARMED_SUFFIX = "warmed";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bff.chat.cache.recent-messages.enabled:true}")
    private boolean enabled;

    @Value("${bff.chat.cache.recent-messages.namespace:spring:chat:cache}")
    private String namespace;

    @Value("${bff.chat.cache.recent-messages.max-size:200}")
    private int maxSize;

    @Value("${bff.chat.cache.recent-messages.ttl-seconds:3600}")
    private long ttlSeconds;

    public ChatRecentMessageCacheRepository(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<List<ChatMessageResponse>> findRecentMessages(String roomId, int limit) {
        if (!enabled || limit > maxSize) {
            return Optional.empty();
        }

        try {
            String key = recentMessagesKey(roomId);
            List<String> payloads = stringRedisTemplate.opsForList().range(key, -limit, -1);
            boolean warmed = Boolean.TRUE.equals(stringRedisTemplate.hasKey(warmedKey(roomId)));

            if (payloads == null || payloads.isEmpty()) {
                if (warmed) {
                    return Optional.of(List.of());
                }
                return Optional.empty();
            }

            if (!warmed && payloads.size() < limit) {
                return Optional.empty();
            }

            return Optional.of(payloads.stream()
                    .map(this::deserialize)
                    .toList());

        } catch (RuntimeException e) {
            log.warn("Failed to read chat recent message cache. roomId={}", roomId, e);
            return Optional.empty();
        }
    }

    public void replaceRecentMessages(String roomId, List<ChatMessageResponse> messages) {
        if (!enabled) {
            return;
        }

        try {
            String key = recentMessagesKey(roomId);
            stringRedisTemplate.delete(key);

            List<String> payloads = messages.stream()
                    .skip(Math.max(messages.size() - maxSize, 0))
                    .map(this::serialize)
                    .toList();

            if (!payloads.isEmpty()) {
                stringRedisTemplate.opsForList().rightPushAll(key, payloads);
                stringRedisTemplate.expire(key, ttl());
            }

            stringRedisTemplate.opsForValue().set(warmedKey(roomId), "1", ttl());

        } catch (RuntimeException e) {
            log.warn("Failed to replace chat recent message cache. roomId={}", roomId, e);
        }
    }

    public void appendMessage(ChatMessageResponse message) {
        if (!enabled) {
            return;
        }

        String roomId = message.roomId();

        try {
            String key = recentMessagesKey(roomId);
            stringRedisTemplate.opsForList().rightPush(key, serialize(message));
            stringRedisTemplate.opsForList().trim(key, -maxSize, -1);
            stringRedisTemplate.expire(key, ttl());

            String warmedKey = warmedKey(roomId);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(warmedKey))) {
                stringRedisTemplate.expire(warmedKey, ttl());
            }

        } catch (RuntimeException e) {
            log.warn("Failed to append chat recent message cache. roomId={}", roomId, e);
        }
    }

    private String recentMessagesKey(String roomId) {
        return namespace + ":room:" + roomId + ":" + RECENT_MESSAGES_SUFFIX;
    }

    private String warmedKey(String roomId) {
        return namespace + ":room:" + roomId + ":" + RECENT_MESSAGES_SUFFIX + ":" + WARMED_SUFFIX;
    }

    private Duration ttl() {
        return Duration.ofSeconds(Math.max(ttlSeconds, 1));
    }

    private String serialize(ChatMessageResponse message) {
        try {
            return objectMapper.writeValueAsString(message);

        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize chat recent message cache", e);
        }
    }

    private ChatMessageResponse deserialize(@Nullable String payload) {
        if (payload == null) {
            throw new IllegalStateException("Chat recent message cache payload is null");
        }

        try {
            return objectMapper.readValue(payload, ChatMessageResponse.class);

        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize chat recent message cache", e);
        }
    }
}
