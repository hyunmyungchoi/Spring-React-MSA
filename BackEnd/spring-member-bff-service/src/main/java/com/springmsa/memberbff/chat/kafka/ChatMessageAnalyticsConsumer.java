package com.springmsa.memberbff.chat.kafka;

import com.springmsa.kafka.event.ChatMessageCreatedEvent;
import com.springmsa.kafka.topic.MsaKafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageAnalyticsConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = MsaKafkaTopics.CHAT_MESSAGE_CREATED,
            groupId = "${bff.kafka.consumer.analytics-group:spring-member-bff-chat-analytics}"
    )
    public void handle(String payload) {
        ChatMessageCreatedEvent event = parse(payload);

        log.info(
                "Consumed chat analytics event. eventId={}, roomId={}, messageId={}, sentAt={}",
                event.eventId(),
                event.roomId(),
                event.messageId(),
                event.sentAt()
        );
    }

    private ChatMessageCreatedEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, ChatMessageCreatedEvent.class);

        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid chat message analytics event payload", e);
        }
    }
}
