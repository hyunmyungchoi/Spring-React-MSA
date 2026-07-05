package com.springmsa.memberbff.chat.kafka;

import com.springmsa.kafka.event.ChatMessageCreatedEvent;
import com.springmsa.kafka.topic.MsaKafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatMessageAnalyticsConsumer {

    @KafkaListener(
            topics = MsaKafkaTopics.CHAT_MESSAGE_CREATED,
            groupId = "${bff.kafka.consumer.analytics-group:spring-member-bff-chat-analytics}"
    )
    public void handle(ChatMessageCreatedEvent event) {
        log.info(
                "Consumed chat analytics event. eventId={}, roomId={}, messageId={}, sentAt={}",
                event.eventId(),
                event.roomId(),
                event.messageId(),
                event.sentAt()
        );
    }
}
