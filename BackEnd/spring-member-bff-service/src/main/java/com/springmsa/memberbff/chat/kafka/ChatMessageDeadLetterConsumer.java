package com.springmsa.memberbff.chat.kafka;

import com.springmsa.kafka.event.ChatMessageCreatedEvent;
import com.springmsa.kafka.topic.MsaKafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatMessageDeadLetterConsumer {

    @KafkaListener(
            topics = MsaKafkaTopics.CHAT_MESSAGE_CREATED_DLT,
            groupId = "${bff.kafka.consumer.dlt-group:spring-member-bff-chat-dlt}"
    )
    public void handle(ChatMessageCreatedEvent event) {
        log.warn(
                "Consumed chat dead-letter event. eventId={}, roomId={}, messageId={}",
                event.eventId(),
                event.roomId(),
                event.messageId()
        );
    }
}
