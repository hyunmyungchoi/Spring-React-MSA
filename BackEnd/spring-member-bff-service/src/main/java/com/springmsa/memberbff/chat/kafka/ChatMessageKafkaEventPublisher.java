package com.springmsa.memberbff.chat.kafka;

import com.springmsa.kafka.event.ChatMessageCreatedEvent;
import com.springmsa.kafka.topic.MsaKafkaTopics;
import com.springmsa.memberbff.chat.dto.ChatMessageResponse;
import com.springmsa.memberbff.chat.event.ChatMessageSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageKafkaEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ChatMessageSavedEvent event) {
        ChatMessageCreatedEvent payload = toPayload(event.message());
        String serializedPayload = serialize(payload);

        kafkaTemplate.send(MsaKafkaTopics.CHAT_MESSAGE_CREATED, payload.roomId(), serializedPayload)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.warn("Failed to publish chat message event. eventId={}", payload.eventId(), exception);
                        return;
                    }

                    log.debug(
                            "Published chat message event. topic={}, partition={}, offset={}, eventId={}",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            payload.eventId()
                    );
                });
    }

    private ChatMessageCreatedEvent toPayload(ChatMessageResponse message) {
        Long messageId = parseMessageId(message.streamId());

        return new ChatMessageCreatedEvent(
                "chat-message-created:" + message.streamId(),
                messageId,
                message.roomId(),
                message.senderUserId(),
                message.senderLoginId(),
                message.senderName(),
                message.content(),
                message.sentAt(),
                Instant.now()
        );
    }

    private Long parseMessageId(String value) {
        try {
            return Long.parseLong(value);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String serialize(ChatMessageCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);

        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize chat message event", e);
        }
    }
}
