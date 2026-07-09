package com.springmsa.authserver.login;

import com.springmsa.authserver.login.service.LoginAuthenticationFactory;
import com.springmsa.authserver.user.dto.AuthUserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAuthenticationFactoryTest {

    private final LoginAuthenticationFactory factory = new LoginAuthenticationFactory();

    @Test
    void createsPasswordFactorAuthentication() {
        Authentication authentication = factory.createPasswordAuthentication(
                authUser(),
                new MockHttpServletRequest()
        );

        assertThat(authorities(authentication))
                .contains("ROLE_USER", FactorGrantedAuthority.PASSWORD_AUTHORITY)
                .doesNotContain(FactorGrantedAuthority.OTT_AUTHORITY);
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
