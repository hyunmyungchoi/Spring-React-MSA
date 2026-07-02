package com.springmsa.adminbff.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminBffOAuth2ClientService {

    public static final String REGISTRATION_ID = "admin-bff";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final AdminBffAuthenticationService adminBffAuthenticationService;

    public String getAccessToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        if (!adminBffAuthenticationService.isAuthenticated(authentication)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin BFF session has no OAuth2 authentication");
        }

        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(authentication)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response)
                .build();

        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);

        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin BFF session has no access token");
        }

        return authorizedClient.getAccessToken().getTokenValue();
    }

    public Optional<String> getIdToken(Authentication authentication) {
        if (!adminBffAuthenticationService.isAuthenticated(authentication)) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof OidcUser oidcUser && oidcUser.getIdToken() != null) {
            return Optional.of(oidcUser.getIdToken().getTokenValue());
        }

        return Optional.empty();
    }
}
