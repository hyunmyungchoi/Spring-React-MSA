package com.springmsa.memberbff.chat.redis;

import com.springmsa.memberbff.auth.dto.SessionUserResponse;
import com.springmsa.memberbff.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatRedisStreamRepository {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${bff.chat.stream.namespace:spring:chat}")
    private String chatStreamNamespace;

    @Value("${bff.chat.stream.max-length:1000}")
    private long chatStreamMaxLength;

    public ChatMessageResponse append(String roomId, SessionUserResponse sender, String content, Instant sentAt) {
        String streamKey = streamKey(roomId);
        Map<String, String> fields = messageFields(roomId, sender, content, sentAt);
        RecordId recordId = stringRedisTemplate.opsForStream().add(streamKey, fields);

        if (chatStreamMaxLength > 0) {
            stringRedisTemplate.opsForStream().trim(streamKey, chatStreamMaxLength, true);
        }

        return new ChatMessageResponse(
                recordId == null ? "0-0" : recordId.getValue(),
                roomId,
                sender.userId(),
                sender.loginId(),
                senderName(sender),
                content,
                sentAt
        );
    }

    public List<ChatMessageResponse> findRecentMessages(String roomId, int limit) {
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .reverseRange(streamKey(roomId), Range.unbounded(), Limit.limit().count(limit));

        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<ChatMessageResponse> messages = new ArrayList<>(records.stream()
                .map(this::toMessage)
                .toList());
        messages.sort(this::compareStreamIds);

        return List.copyOf(messages);
    }

    public List<ChatMessageResponse> readMessagesAfter(String roomId, String lastMessageId, int limit) {
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                StreamReadOptions.empty().count(limit),
                StreamOffset.create(streamKey(roomId), ReadOffset.from(lastMessageId))
        );

        if (records == null || records.isEmpty()) {
            return List.of();
        }

        return records.stream()
                .map(this::toMessage)
                .toList();
    }

    public Optional<String> findLatestMessageId(String roomId) {
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .reverseRange(streamKey(roomId), Range.unbounded(), Limit.limit().count(1));

        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(records.get(0).getId().getValue());
    }

    private String streamKey(String roomId) {
        return chatStreamNamespace + ":room:" + roomId + ":messages";
    }

    private Map<String, String> messageFields(String roomId, SessionUserResponse sender, String content, Instant sentAt) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("roomId", roomId);
        putIfPresent(fields, "senderUserId", sender.userId());
        putIfPresent(fields, "senderLoginId", sender.loginId());
        putIfPresent(fields, "senderName", senderName(sender));
        fields.put("content", content);
        fields.put("sentAt", sentAt.toString());
        return fields;
    }

    private void putIfPresent(Map<String, String> fields, String fieldName, Object value) {
        if (value == null) {
            return;
        }

        String stringValue = String.valueOf(value);

        if (StringUtils.hasText(stringValue)) {
            fields.put(fieldName, stringValue);
        }
    }

    private String senderName(SessionUserResponse sender) {
        if (StringUtils.hasText(sender.username())) {
            return sender.username();
        }

        if (StringUtils.hasText(sender.name())) {
            return sender.name();
        }

        if (StringUtils.hasText(sender.loginId())) {
            return sender.loginId();
        }

        return sender.sub();
    }

    private ChatMessageResponse toMessage(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();

        return new ChatMessageResponse(
                record.getId().getValue(),
                stringValue(value.get("roomId")),
                longValue(value.get("senderUserId")),
                stringValue(value.get("senderLoginId")),
                stringValue(value.get("senderName")),
                stringValue(value.get("content")),
                instantValue(value.get("sentAt"))
        );
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value);
    }

    private Long longValue(Object value) {
        String stringValue = stringValue(value);

        if (!StringUtils.hasText(stringValue)) {
            return null;
        }

        try {
            return Long.parseLong(stringValue);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Instant instantValue(Object value) {
        String stringValue = stringValue(value);

        if (!StringUtils.hasText(stringValue)) {
            return Instant.EPOCH;
        }

        try {
            return Instant.parse(stringValue);

        } catch (RuntimeException e) {
            return Instant.EPOCH;
        }
    }

    private int compareStreamIds(ChatMessageResponse left, ChatMessageResponse right) {
        return compareStreamId(left.streamId(), right.streamId());
    }

    private int compareStreamId(String left, String right) {
        String[] leftParts = left.split("-", 2);
        String[] rightParts = right.split("-", 2);

        int millisComparison = Long.compare(longPart(leftParts, 0), longPart(rightParts, 0));

        if (millisComparison != 0) {
            return millisComparison;
        }

        return Long.compare(longPart(leftParts, 1), longPart(rightParts, 1));
    }

    private long longPart(String[] parts, int index) {
        if (parts.length <= index) {
            return 0;
        }

        try {
            return Long.parseLong(parts[index]);

        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

