package com.springmsa.adminbff.session.service;

import com.springmsa.adminbff.session.dto.MemberPresenceEventResponse;
import com.springmsa.adminbff.session.dto.MemberSessionResponse;
import com.springmsa.adminbff.session.redis.MemberPresenceEventData;
import com.springmsa.adminbff.session.redis.MemberSessionRedisData;
import com.springmsa.adminbff.session.redis.MemberSessionRedisRepository;
import com.springmsa.adminbff.session.redis.MemberPresenceRedisRepository;
import com.springmsa.adminbff.session.redis.MemberSessionPresence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberSessionService {

    private final MemberSessionRedisRepository memberSessionRedisRepository;
    private final MemberPresenceRedisRepository memberPresenceRedisRepository;

    public List<MemberSessionResponse> findMemberSessions() {
        return memberSessionRedisRepository.findMemberSessions().stream()
                .map(this::toResponse)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(this::lastAccessedAtOrEpoch).reversed())
                .toList();
    }

    public List<MemberPresenceEventResponse> findMemberPresenceEvents() {
        return memberPresenceRedisRepository.findRecentEvents().stream()
                .map(this::toResponse)
                .toList();
    }

    private Optional<MemberSessionResponse> toResponse(MemberSessionRedisData session) {
        if (!hasMemberIdentity(session)) {
            return Optional.empty();
        }

        Instant expiresAt = expiresAt(session.lastAccessedAt(), session.maxInactiveIntervalSeconds());

        if (isExpired(expiresAt)) {
            return Optional.empty();
        }

        MemberSessionPresence presence = memberPresenceRedisRepository.findBySessionId(session.sessionId());

        return Optional.of(new MemberSessionResponse(
                session.sessionId(),
                session.userId(),
                session.loginId(),
                session.name(),
                session.username(),
                session.email(),
                session.roles(),
                session.createdAt(),
                session.lastAccessedAt(),
                session.maxInactiveIntervalSeconds(),
                expiresAt,
                presence.online(),
                presence.lastHeartbeatAt(),
                presence.onlineExpiresAt(),
                presence.onlineTtlSeconds()
        ));
    }

    private MemberPresenceEventResponse toResponse(MemberPresenceEventData event) {
        return new MemberPresenceEventResponse(
                event.streamId(),
                event.eventType(),
                event.sessionFingerprint(),
                event.userId(),
                event.loginId(),
                event.username(),
                event.roles(),
                event.occurredAt()
        );
    }

    private boolean hasMemberIdentity(MemberSessionRedisData session) {
        return session.userId() != null || session.loginId() != null;
    }

    private Instant expiresAt(Instant lastAccessedAt, long maxInactiveIntervalSeconds) {
        if (lastAccessedAt == null || maxInactiveIntervalSeconds <= 0) {
            return null;
        }

        return lastAccessedAt.plusSeconds(maxInactiveIntervalSeconds);
    }

    private boolean isExpired(Instant expiresAt) {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    private Instant lastAccessedAtOrEpoch(MemberSessionResponse response) {
        return response.lastAccessedAt() == null ? Instant.EPOCH : response.lastAccessedAt();
    }
}
