package com.springmsa.adminbff.auth.service;

import com.springmsa.adminbff.auth.dto.AdminSessionUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminBffAuthenticationService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AdminPrincipalClaimsMapper adminPrincipalClaimsMapper;

    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    public boolean hasAdminRole(Authentication authentication) {
        return isAuthenticated(authentication)
                && adminPrincipalClaimsMapper.hasRole(authentication, ROLE_ADMIN);
    }

    public AdminSessionUserResponse getSessionUser(Authentication authentication) {
        return adminPrincipalClaimsMapper.toSessionUser(authentication);
    }
}
