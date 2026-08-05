package com.springmsa.memberbff.kafka.consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class KafkaEventDeduplicator {

    private final JdbcTemplate jdbcTemplate;

    public KafkaEventDeduplicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claim(UUID eventId, String eventType, String consumerGroup) {
        return jdbcTemplate.update("""
                        INSERT INTO member_bff.processed_kafka_events (event_id, event_type, consumer_group, processed_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (event_id) DO NOTHING
                        """,
                eventId, eventType, consumerGroup, OffsetDateTime.now(ZoneOffset.UTC)) == 1;
    }
}
