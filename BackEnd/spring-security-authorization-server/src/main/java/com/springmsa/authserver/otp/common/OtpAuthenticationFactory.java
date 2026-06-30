package com.springmsa.authserver.otp.common;

import com.springmsa.authserver.client.dto.AuthUserResponse;
import com.springmsa.authserver.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

@Component
public class OtpAuthenticationFactory {

    public Authentication createAuthentication(
            AuthUserResponse user,
            HttpServletRequest request,
            String factorAuthority
    ) {
        Set<String> roles = user.roles() == null ? Set.of() : user.roles();

        Collection<GrantedAuthority> authorities = new ArrayList<>();

        roles.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        authorities.add(
                FactorGrantedAuthority.fromAuthority(factorAuthority)
        );

        CustomUserPrincipal principal = new CustomUserPrincipal(
                user.userId(),
                user.loginId(),
                user.email(),
                user.username(),
                user.password() == null ? "" : user.password(),
                user.enabled(),
                authorities
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        return authentication;
    }
}
