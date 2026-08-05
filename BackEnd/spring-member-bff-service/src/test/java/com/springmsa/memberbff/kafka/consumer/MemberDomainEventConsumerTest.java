package com.springmsa.memberbff.kafka.consumer;

import com.springmsa.kafka.event.MsaEventEnvelope;
import com.springmsa.kafka.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberDomainEventConsumerTest {

    @Mock
    KafkaEventDeduplicator eventDeduplicator;

    @Mock
    MemberNotificationWriter notificationWriter;

    MemberDomainEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MemberDomainEventConsumer(
                JsonMapper.builder().build(), eventDeduplicator, notificationWriter
        );
    }

    @Test
    void writesOneNotificationForANewEvent() throws Exception {
        MsaEventEnvelope<UserRegisteredEvent> event = MsaEventEnvelope.create(
                "user.registered",
                1,
                "spring-user-service",
                Instant.parse("2026-08-06T00:00:00Z"),
                new UserRegisteredEvent(1L, "member1", "member1@example.com", "Member One", Set.of("ROLE_USER"))
        );
        when(eventDeduplicator.claim(event.eventId(), event.eventType(), "spring-member-bff-domain-notification"))
                .thenReturn(true);

        consumer.consume(JsonMapper.builder().build().writeValueAsString(event));

        verify(notificationWriter).write(
                event.eventId(), "member1", "user.registered", "Welcome",
                "Welcome, Member One. Your account is ready."
        );
    }

    @Test
    void skipsAnAlreadyClaimedEvent() throws Exception {
        MsaEventEnvelope<UserRegisteredEvent> event = MsaEventEnvelope.create(
                "user.registered",
                1,
                "spring-user-service",
                Instant.parse("2026-08-06T00:00:00Z"),
                new UserRegisteredEvent(1L, "member1", "member1@example.com", "Member One", Set.of("ROLE_USER"))
        );
        when(eventDeduplicator.claim(event.eventId(), event.eventType(), "spring-member-bff-domain-notification"))
                .thenReturn(false);

        consumer.consume(JsonMapper.builder().build().writeValueAsString(event));

        verify(notificationWriter, never()).write(any(), any(), any(), any(), any());
    }
}
