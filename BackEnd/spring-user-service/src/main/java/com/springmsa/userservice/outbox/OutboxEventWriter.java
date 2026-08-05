package com.springmsa.userservice.outbox;

import com.springmsa.kafka.event.MsaEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.ZoneOffset;

@Component
public class OutboxEventWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void append(String aggregateType, String aggregateId, String topic, String eventKey,
                       MsaEventEnvelope<?> event) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO user_service.outbox_events (
                        event_id, aggregate_type, aggregate_id, event_type, topic, event_key,
                        payload, occurred_at, attempts
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    event.eventId(), aggregateType, aggregateId, event.eventType(), topic, eventKey,
                    objectMapper.writeValueAsString(event), event.occurredAt().atOffset(ZoneOffset.UTC));
        } catch (Exception exception) {
            log.error("Failed to persist outbox event. eventId={}, topic={}", event.eventId(), topic, exception);
            throw new IllegalStateException("Failed to serialize and persist outbox event", exception);
        }
    }
}
