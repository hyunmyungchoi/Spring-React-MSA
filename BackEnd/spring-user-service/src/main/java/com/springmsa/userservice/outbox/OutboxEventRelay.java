package com.springmsa.userservice.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OutboxEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRelay.class);

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final int batchSize;
    private final long sendTimeoutSeconds;

    public OutboxEventRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<Object, Object> kafkaTemplate,
                            @Value("${app.kafka.outbox.batch-size:100}") int batchSize,
                            @Value("${app.kafka.outbox.send-timeout-seconds:10}") long sendTimeoutSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox.fixed-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        List<PendingEvent> events = jdbcTemplate.query("""
                        SELECT event_id, topic, event_key, payload FROM user_service.outbox_events
                        WHERE published_at IS NULL ORDER BY occurred_at, event_id
                        LIMIT ? FOR UPDATE SKIP LOCKED
                        """,
                (resultSet, rowNum) -> new PendingEvent(
                        resultSet.getObject("event_id", UUID.class), resultSet.getString("topic"),
                        resultSet.getString("event_key"), resultSet.getString("payload")), batchSize);

        for (PendingEvent event : events) {
            try {
                kafkaTemplate.send(event.topic(), event.eventKey(), event.payload())
                        .get(sendTimeoutSeconds, TimeUnit.SECONDS);
                jdbcTemplate.update(
                        "UPDATE user_service.outbox_events SET published_at = ?, attempts = attempts + 1, last_error = NULL WHERE event_id = ?",
                        OffsetDateTime.now(ZoneOffset.UTC), event.eventId());
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                jdbcTemplate.update(
                        "UPDATE user_service.outbox_events SET attempts = attempts + 1, last_error = ? WHERE event_id = ?",
                        message.substring(0, Math.min(message.length(), 1000)), event.eventId());
                log.warn("Outbox publish failed. eventId={}, topic={}", event.eventId(), event.topic(), exception);
            }
        }
    }

    private record PendingEvent(UUID eventId, String topic, String eventKey, String payload) {
    }
}
