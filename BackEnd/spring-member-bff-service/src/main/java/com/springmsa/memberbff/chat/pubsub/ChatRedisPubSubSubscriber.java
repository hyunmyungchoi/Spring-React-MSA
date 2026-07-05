package com.springmsa.memberbff.chat.pubsub;

import com.springmsa.memberbff.chat.ChatWebSocketSessionRegistry;
import com.springmsa.memberbff.chat.dto.ChatServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRedisPubSubSubscriber implements MessageListener {

    private static final String MESSAGE_TYPE_CHAT = "CHAT_MESSAGE";

    private final ObjectMapper objectMapper;
    private final ChatWebSocketSessionRegistry chatWebSocketSessionRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            ChatServerMessage serverMessage = objectMapper.readValue(payload, ChatServerMessage.class);

            if (!MESSAGE_TYPE_CHAT.equals(serverMessage.type()) || serverMessage.message() == null) {
                return;
            }

            chatWebSocketSessionRegistry.broadcast(serverMessage.roomId(), serverMessage);

        } catch (RuntimeException e) {
            log.warn("Failed to handle chat pub/sub message. payload={}", payload, e);
        }
    }
}
