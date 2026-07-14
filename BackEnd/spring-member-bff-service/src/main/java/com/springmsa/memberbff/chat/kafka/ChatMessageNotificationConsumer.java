package com.springmsa.memberbff.chat.kafka;

import com.springmsa.kafka.event.ChatMessageCreatedEvent;
import com.springmsa.kafka.topic.MsaKafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ChatMessageNotificationConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = MsaKafkaTopics.CHAT_MESSAGE_CREATED,
            groupId = "${bff.kafka.consumer.notification-group:spring-member-bff-chat-notification}"
    )
    public void handle(String payload) {
        ChatMessageCreatedEvent event = parse(payload);

        log.info(
                "Consumed chat notification event. eventId={}, roomId={}, senderLoginId={}",
                event.eventId(),
                event.roomId(),
                event.senderLoginId()
        );
    }

    private ChatMessageCreatedEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, ChatMessageCreatedEvent.class);

        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid chat message notification event payload", e);
        }
    }
}
