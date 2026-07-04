package com.springmsa.memberbff.chat;

import com.springmsa.memberbff.chat.dto.ChatMessageResponse;
import com.springmsa.memberbff.chat.dto.ChatServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStreamBroadcastScheduler {

    private static final String INITIAL_STREAM_ID = "0-0";
    private static final int READ_BATCH_SIZE = 100;

    private final ChatMessageService chatMessageService;
    private final ChatWebSocketSessionRegistry chatWebSocketSessionRegistry;

    private final ConcurrentMap<String, String> lastMessageIdsByRoom = new ConcurrentHashMap<>();

    public void watchRoom(String roomId) {
        lastMessageIdsByRoom.computeIfAbsent(roomId, this::findLatestMessageId);
    }

    @Scheduled(fixedDelayString = "${bff.chat.stream.poll-delay-ms:500}")
    public void broadcastNewMessages() {
        Set<String> activeRoomIds = chatWebSocketSessionRegistry.activeRoomIds();

        if (activeRoomIds.isEmpty()) {
            lastMessageIdsByRoom.clear();
            return;
        }

        lastMessageIdsByRoom.keySet().removeIf(roomId -> !activeRoomIds.contains(roomId));

        for (String roomId : activeRoomIds) {
            broadcastRoomMessages(roomId);
        }
    }

    private void broadcastRoomMessages(String roomId) {
        String lastMessageId = lastMessageIdsByRoom.computeIfAbsent(roomId, this::findLatestMessageId);

        try {
            List<ChatMessageResponse> messages = chatMessageService.readMessagesAfter(roomId, lastMessageId, READ_BATCH_SIZE);

            for (ChatMessageResponse message : messages) {
                chatWebSocketSessionRegistry.broadcast(roomId, ChatServerMessage.chat(roomId, message));
                lastMessageId = message.streamId();
            }

            lastMessageIdsByRoom.put(roomId, lastMessageId);

        } catch (RuntimeException e) {
            log.warn("Failed to broadcast chat stream messages. roomId={}", roomId, e);
        }
    }

    private String findLatestMessageId(String roomId) {
        return chatMessageService.findLatestMessageId(roomId).orElse(INITIAL_STREAM_ID);
    }
}

