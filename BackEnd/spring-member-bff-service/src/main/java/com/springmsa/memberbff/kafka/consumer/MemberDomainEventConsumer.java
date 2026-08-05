package com.springmsa.memberbff.kafka.consumer;

import com.springmsa.kafka.topic.MsaKafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class MemberDomainEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MemberDomainEventConsumer.class);
    private static final String CONSUMER_GROUP = "spring-member-bff-domain-notification";

    private final ObjectMapper objectMapper;
    private final KafkaEventDeduplicator eventDeduplicator;
    private final MemberNotificationWriter notificationWriter;

    public MemberDomainEventConsumer(
            ObjectMapper objectMapper,
            KafkaEventDeduplicator eventDeduplicator,
            MemberNotificationWriter notificationWriter
    ) {
        this.objectMapper = objectMapper;
        this.eventDeduplicator = eventDeduplicator;
        this.notificationWriter = notificationWriter;
    }

    @KafkaListener(
            topics = {
                    MsaKafkaTopics.COMMUNITY_POST_CREATED_V1,
                    MsaKafkaTopics.USER_REGISTERED_V1,
                    MsaKafkaTopics.WATCHLIST_ITEM_ADDED_V1
            },
            groupId = CONSUMER_GROUP
    )
    @Transactional
    public void consume(String message) throws Exception {
        JsonNode envelope = objectMapper.readTree(message);
        UUID eventId = UUID.fromString(requiredText(envelope, "eventId"));
        String eventType = requiredText(envelope, "eventType");

        if (!eventDeduplicator.claim(eventId, eventType, CONSUMER_GROUP)) {
            log.debug("Skipping already processed event. eventId={}, eventType={}", eventId, eventType);
            return;
        }

        JsonNode payload = envelope.get("payload");
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("Kafka event payload is required");
        }

        switch (eventType) {
            case "community.post-created" -> notificationWriter.write(
                    eventId,
                    requiredText(payload, "ownerSub"),
                    eventType,
                    "Community post created",
                    "Post '" + requiredText(payload, "title") + "' was created successfully."
            );
            case "user.registered" -> notificationWriter.write(
                    eventId,
                    requiredText(payload, "loginId"),
                    eventType,
                    "Welcome",
                    "Welcome, " + requiredText(payload, "username") + ". Your account is ready."
            );
            case "watchlist.item-added" -> notificationWriter.write(
                    eventId,
                    requiredText(payload, "ownerSub"),
                    eventType,
                    "Watchlist updated",
                    requiredText(payload, "symbol") + " was added to your watchlist."
            );
            default -> throw new IllegalArgumentException("Unsupported event type: " + eventType);
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            throw new IllegalArgumentException("Kafka event field is required: " + fieldName);
        }
        return value.asString();
    }
}
