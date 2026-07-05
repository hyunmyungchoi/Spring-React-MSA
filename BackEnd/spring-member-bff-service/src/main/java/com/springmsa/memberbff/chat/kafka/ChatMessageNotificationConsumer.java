package com.springmsa.memberbff.chat.kafka;

import com.springmsa.kafka.event.ChatMessageCreatedEvent;
import com.springmsa.kafka.topic.MsaKafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatMessageNotificationConsumer {

    @KafkaListener(
            topics = MsaKafkaTopics.CHAT_MESSAGE_CREATED,
            groupId = "${bff.kafka.consumer.notification-group:spring-member-bff-chat-notification}"
    )
    public void handle(ChatMessageCreatedEvent event) {
        log.info(
                "Consumed chat notification event. eventId={}, roomId={}, senderLoginId={}",
                event.eventId(),
                event.roomId(),
                event.senderLoginId()
        );
    }
}
