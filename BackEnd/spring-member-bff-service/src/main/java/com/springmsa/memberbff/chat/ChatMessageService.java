package com.springmsa.memberbff.chat;

import com.springmsa.memberbff.auth.dto.SessionUserResponse;
import com.springmsa.memberbff.chat.dto.ChatMessageResponse;
import com.springmsa.memberbff.chat.redis.ChatRedisStreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatRoomValidator chatRoomValidator;
    private final ChatRedisStreamRepository chatRedisStreamRepository;

    @Value("${bff.chat.message.default-history-limit:50}")
    private int defaultHistoryLimit;

    @Value("${bff.chat.message.max-content-length:1000}")
    private int maxContentLength;

    public ChatMessageResponse appendMessage(String roomId, SessionUserResponse sender, String content) {
        String resolvedRoomId = resolveRoomId(roomId);
        String resolvedContent = resolveContent(content);

        return chatRedisStreamRepository.append(resolvedRoomId, sender, resolvedContent, Instant.now());
    }

    public List<ChatMessageResponse> findRecentMessages(String roomId, Integer limit) {
        String resolvedRoomId = resolveRoomId(roomId);
        int resolvedLimit = resolveLimit(limit);

        return chatRedisStreamRepository.findRecentMessages(resolvedRoomId, resolvedLimit);
    }

    public List<ChatMessageResponse> readMessagesAfter(String roomId, String lastMessageId, int limit) {
        String resolvedRoomId = resolveRoomId(roomId);
        int resolvedLimit = Math.max(limit, 1);

        return chatRedisStreamRepository.readMessagesAfter(resolvedRoomId, lastMessageId, resolvedLimit);
    }

    public Optional<String> findLatestMessageId(String roomId) {
        return chatRedisStreamRepository.findLatestMessageId(resolveRoomId(roomId));
    }

    public String resolveRoomId(String roomId) {
        try {
            return chatRoomValidator.resolve(roomId);

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private String resolveContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message content is required");
        }

        String resolvedContent = content.trim();

        if (resolvedContent.length() > maxContentLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message content is too long");
        }

        return resolvedContent;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return Math.max(defaultHistoryLimit, 1);
        }

        return Math.min(limit, 200);
    }
}

