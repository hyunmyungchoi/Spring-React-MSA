package com.springmsa.authserver.otp.common;

import com.springmsa.authserver.client.dto.AuthUserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OtpAuthenticationFactoryTest {

    private final OtpAuthenticationFactory factory = new OtpAuthenticationFactory();

    @Test
    void createsPasswordFactorAuthentication() {
        Authentication authentication = factory.createAuthentication(
                authUser(),
                new MockHttpServletRequest(),
                FactorGrantedAuthority.PASSWORD_AUTHORITY
        );

        assertThat(authorities(authentication))
                .contains("ROLE_USER", FactorGrantedAuthority.PASSWORD_AUTHORITY)
                .doesNotContain(FactorGrantedAuthority.OTT_AUTHORITY);
    }

    @Test
    void createsOneTimeTokenFactorAuthentication() {
        Authentication authentication = factory.createAuthentication(
                authUser(),
                new MockHttpServletRequest(),
                FactorGrantedAuthority.OTT_AUTHORITY
        );

        assertThat(authorities(authentication))
                .contains("ROLE_USER", FactorGrantedAuthority.OTT_AUTHORITY)
                .doesNotContain(FactorGrantedAuthority.PASSWORD_AUTHORITY);
    }

    private AuthUserResponse authUser() {
        return new AuthUserResponse(
                1L,
                "user01",
                "user01@example.com",
                "User 01",
                "{noop}password",
                true,
                Set.of("ROLE_USER")
        );
    }

    private Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
    }
}
