package com.springmsa.memberbff.chat.pubsub;

import com.springmsa.memberbff.chat.dto.ChatServerMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ChatRedisPubSubPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bff.chat.pubsub.channel:spring:chat:broadcast}")
    private String chatBroadcastChannel;

    public void publish(ChatServerMessage message) {
        try {
            stringRedisTemplate.convertAndSend(chatBroadcastChannel, objectMapper.writeValueAsString(message));

        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize chat pub/sub message", e);
        }
    }
}
