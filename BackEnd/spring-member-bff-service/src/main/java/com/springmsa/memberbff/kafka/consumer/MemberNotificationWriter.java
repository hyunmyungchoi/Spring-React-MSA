package com.springmsa.memberbff.kafka.consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class MemberNotificationWriter {

    private final JdbcTemplate jdbcTemplate;

    public MemberNotificationWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(UUID eventId, String recipientSub, String type, String title, String message) {
        jdbcTemplate.update("""
                        INSERT INTO member_bff.member_notifications (
                            event_id, recipient_sub, notification_type, title, message, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                eventId, recipientSub, type, title, message, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
