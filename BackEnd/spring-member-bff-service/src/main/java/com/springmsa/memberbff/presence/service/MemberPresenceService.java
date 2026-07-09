package com.springmsa.memberbff.presence.service;

import com.springmsa.memberbff.auth.dto.SessionUserResponse;
import com.springmsa.memberbff.auth.service.BffAuthenticationService;
import com.springmsa.memberbff.presence.domain.MemberPresenceEventType;
import com.springmsa.memberbff.presence.dto.HeartbeatResponse;
import com.springmsa.memberbff.presence.redis.MemberPresenceRedisRepository;
import com.springmsa.memberbff.presence.redis.MemberPresenceStreamEvent;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class MemberPresenceService {

    private static final String SESSION_FINGERPRINT_ALGORITHM = "SHA-256";

    private final BffAuthenticationService bffAuthenticationService;
    private final MemberPresenceRedisRepository memberPresenceRedisRepository;

    @Value("${bff.presence.heartbeat.ttl-seconds:45}")
    private long heartbeatTtlSeconds;

    public HeartbeatResponse heartbeat(HttpSession session, Authentication authentication) {
        return updateOnlinePresence(session, authentication);
    }

    public HeartbeatResponse login(HttpSession session, Authentication authentication) {
        HeartbeatResponse response = updateOnlinePresence(session, authentication);
        memberPresenceRedisRepository.appendEvent(event(MemberPresenceEventType.LOGIN, session.getId(), authentication, response.heartbeatAt()));
        return response;
    }

    public void logout(HttpSession session, Authentication authentication) {
        if (session == null) {
            return;
        }

        if (bffAuthenticationService.isAuthenticated(authentication)) {
            memberPresenceRedisRepository.appendEvent(event(MemberPresenceEventType.LOGOUT, session.getId(), authentication));
        }

        memberPresenceRedisRepository.deleteOnlineSession(session.getId());
    }

    public void clear(HttpSession session) {
        if (session == null) {
            return;
        }

        memberPresenceRedisRepository.deleteOnlineSession(session.getId());
    }

    private HeartbeatResponse updateOnlinePresence(HttpSession session, Authentication authentication) {
        if (session == null || !bffAuthenticationService.isAuthenticated(authentication)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member BFF session is required");
        }

        long ttlSeconds = Math.max(heartbeatTtlSeconds, 1);
        Instant heartbeatAt = Instant.now();
        Instant expiresAt = heartbeatAt.plusSeconds(ttlSeconds);

        memberPresenceRedisRepository.saveOnlineSession(session.getId(), heartbeatAt, ttlSeconds);

        return new HeartbeatResponse(true, heartbeatAt, expiresAt, ttlSeconds);
    }

    private MemberPresenceStreamEvent event(MemberPresenceEventType eventType, String sessionId, Authentication authentication) {
        return event(eventType, sessionId, authentication, Instant.now());
    }

    private MemberPresenceStreamEvent event(MemberPresenceEventType eventType, String sessionId, Authentication authentication, Instant occurredAt) {
        SessionUserResponse user = bffAuthenticationService.getSessionUser(authentication);

        return new MemberPresenceStreamEvent(
                eventType,
                sessionFingerprint(sessionId),
                user.userId(),
                user.loginId(),
                user.username(),
                user.roles(),
                occurredAt
        );
    }

    private String sessionFingerprint(String sessionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SESSION_FINGERPRINT_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(sessionId.getBytes(StandardCharsets.UTF_8)));

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }
}
