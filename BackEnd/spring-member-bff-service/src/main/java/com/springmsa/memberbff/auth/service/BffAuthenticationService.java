package com.springmsa.memberbff.auth.service;

import com.springmsa.memberbff.auth.dto.SessionUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BffAuthenticationService {

    private final BffPrincipalClaimsMapper bffPrincipalClaimsMapper;

    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    public SessionUserResponse getSessionUser(Authentication authentication) {
        return bffPrincipalClaimsMapper.toSessionUser(authentication);
    }
}
