package com.springmsa.authserver.otp.common;

import com.springmsa.authserver.client.dto.AuthUserResponse;
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
            HttpServletRequest request
    ) {
        Set<String> roles = user.roles() == null ? Set.of() : user.roles();

        Collection<GrantedAuthority> authorities = new ArrayList<>();

        roles.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        authorities.add(
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.OTT_AUTHORITY)
        );

        UserDetails principal = User.withUsername(user.loginId())
                .password("")
                .authorities(authorities)
                .disabled(!user.enabled())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .build();

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