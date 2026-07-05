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
public class ChatMessageDeadLetterConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = MsaKafkaTopics.CHAT_MESSAGE_CREATED_DLT,
            groupId = "${bff.kafka.consumer.dlt-group:spring-member-bff-chat-dlt}"
    )
    public void handle(String payload) {
        ChatMessageCreatedEvent event = parse(payload);

        if (event == null) {
            log.warn("Consumed unreadable chat dead-letter event. payload={}", payload);
            return;
        }

        log.warn(
                "Consumed chat dead-letter event. eventId={}, roomId={}, messageId={}",
                event.eventId(),
                event.roomId(),
                event.messageId()
        );
    }

    private ChatMessageCreatedEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, ChatMessageCreatedEvent.class);

        } catch (JacksonException e) {
            return null;
        }
    }
}
