package com.springmsa.adminbff.session.service;

import com.springmsa.adminbff.session.dto.MemberSessionResponse;
import com.springmsa.adminbff.session.redis.MemberPresenceRedisRepository;
import com.springmsa.adminbff.session.redis.MemberSessionPresence;
import com.springmsa.adminbff.session.redis.MemberSessionRedisData;
import com.springmsa.adminbff.session.redis.MemberSessionRedisRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberSessionServiceTest {

    @Test
    void replacesRawSessionIdWithSha256Fingerprint() {
        MemberSessionRedisRepository sessionRepository = mock(MemberSessionRedisRepository.class);
        MemberPresenceRedisRepository presenceRepository = mock(MemberPresenceRedisRepository.class);
        MemberSessionService service = new MemberSessionService(sessionRepository, presenceRepository);

        String rawSessionId = "raw-session-id";
        MemberSessionRedisData session = new MemberSessionRedisData(
                rawSessionId,
                1L,
                "member",
                "Member",
                "member-user",
                "member@example.com",
                List.of("ROLE_USER"),
                Instant.parse("2026-07-18T00:00:00Z"),
                Instant.now(),
                3600
        );

        when(sessionRepository.findMemberSessions()).thenReturn(List.of(session));
        when(presenceRepository.findBySessionId(rawSessionId)).thenReturn(MemberSessionPresence.offline());

        List<MemberSessionResponse> responses = service.findMemberSessions();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).sessionFingerprint())
                .isEqualTo("05d9cb246274b4a830629b686113a7ae314e13ed59da03d22277cfe000ef1f55")
                .doesNotContain(rawSessionId);
    }
}
