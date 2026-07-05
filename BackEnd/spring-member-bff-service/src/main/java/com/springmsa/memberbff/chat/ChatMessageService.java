package com.springmsa.memberbff.chat;

import com.springmsa.memberbff.auth.dto.SessionUserResponse;
import com.springmsa.memberbff.chat.cache.ChatRecentMessageCacheRepository;
import com.springmsa.memberbff.chat.dto.ChatMessageResponse;
import com.springmsa.memberbff.chat.event.ChatMessageSavedEvent;
import com.springmsa.memberbff.chat.persistence.ChatMessageEntity;
import com.springmsa.memberbff.chat.persistence.ChatMessageJpaRepository;
import com.springmsa.memberbff.chat.persistence.ChatRoomEntity;
import com.springmsa.memberbff.chat.persistence.ChatRoomJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatRoomValidator chatRoomValidator;
    private final ChatRoomJpaRepository chatRoomJpaRepository;
    private final ChatMessageJpaRepository chatMessageJpaRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ChatRecentMessageCacheRepository chatRecentMessageCacheRepository;

    private final Object roomCreationLock = new Object();

    @Value("${bff.chat.message.default-history-limit:50}")
    private int defaultHistoryLimit;

    @Value("${bff.chat.message.max-content-length:1000}")
    private int maxContentLength;

    @Transactional
    public ChatMessageResponse appendMessage(String roomId, SessionUserResponse sender, String content) {
        String resolvedRoomId = resolveRoomId(roomId);
        String resolvedContent = resolveContent(content);
        Instant sentAt = Instant.now();
        ChatRoomEntity room = findOrCreateRoom(resolvedRoomId, sentAt);
        ChatMessageEntity message = chatMessageJpaRepository.save(ChatMessageEntity.create(
                room,
                sender.userId(),
                sender.loginId(),
                senderName(sender),
                resolvedContent,
                sentAt
        ));

        ChatMessageResponse response = toResponse(message);
        applicationEventPublisher.publishEvent(new ChatMessageSavedEvent(response));

        return response;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> findRecentMessages(String roomId, Integer limit) {
        String resolvedRoomId = resolveRoomId(roomId);
        int resolvedLimit = resolveLimit(limit);

        List<ChatMessageResponse> cachedMessages = chatRecentMessageCacheRepository
                .findRecentMessages(resolvedRoomId, resolvedLimit)
                .orElse(null);
        if (cachedMessages != null) {
            return cachedMessages;
        }

        List<ChatMessageEntity> messages = new ArrayList<>(chatMessageJpaRepository.findRecentMessages(
                resolvedRoomId,
                PageRequest.of(0, resolvedLimit)
        ));

        Collections.reverse(messages);

        List<ChatMessageResponse> recentMessages = messages.stream()
                .map(this::toResponse)
                .toList();
        chatRecentMessageCacheRepository.replaceRecentMessages(resolvedRoomId, recentMessages);

        return recentMessages;
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

    private ChatRoomEntity findOrCreateRoom(String roomId, Instant createdAt) {
        return chatRoomJpaRepository.findByRoomId(roomId)
                .orElseGet(() -> createRoom(roomId, createdAt));
    }

    private ChatRoomEntity createRoom(String roomId, Instant createdAt) {
        synchronized (roomCreationLock) {
            return chatRoomJpaRepository.findByRoomId(roomId)
                    .orElseGet(() -> chatRoomJpaRepository.save(ChatRoomEntity.create(roomId, createdAt)));
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return Math.max(defaultHistoryLimit, 1);
        }

        return Math.min(limit, 200);
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

    private ChatMessageResponse toResponse(ChatMessageEntity message) {
        return new ChatMessageResponse(
                String.valueOf(message.getId()),
                message.getRoom().getRoomId(),
                message.getSenderUserId(),
                message.getSenderLoginId(),
                message.getSenderName(),
                message.getContent(),
                message.getSentAt()
        );
    }
}
