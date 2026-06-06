package com.springmsa.adminbff.auth;

import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@NullMarked
@RestController
public class AdminBffAuthController {

    private static final String SESSION_OAUTH2_STATE = "ADMIN_OAUTH2_STATE";

    private final AdminBffTokenService adminBffTokenService;

    @Value("${admin-bff.oauth2.authorization-uri}")
    private String authorizationUri;

    @Value("${admin-bff.oauth2.client-id}")
    private String clientId;

    @Value("${admin-bff.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${admin-bff.oauth2.scope}")
    private String scope;

    public AdminBffAuthController(AdminBffTokenService adminBffTokenService) {
        this.adminBffTokenService = adminBffTokenService;
    }

    @GetMapping("/auth/login")
    public RedirectView login(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(SESSION_OAUTH2_STATE, state);

        String authorizeUrl = UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();

        return new RedirectView(authorizeUrl);
    }

    @GetMapping("/auth/callback")
    public Map<String, Object> callback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpSession session
    ) {
        Object savedState = session.getAttribute(SESSION_OAUTH2_STATE);

        if (savedState == null || state == null || !savedState.equals(state)) {
            return Map.of(
                    "login", "failed",
                    "reason", "invalid_state"
            );
        }

        session.removeAttribute(SESSION_OAUTH2_STATE);

        OAuth2TokenResponse tokenResponse = adminBffTokenService.exchangeCodeForToken(code);
        adminBffTokenService.saveTokenResponse(session, tokenResponse);

        return Map.of(
                "login", "success",
                "tokenType", tokenResponse.tokenType(),
                "expiresIn", tokenResponse.expiresIn(),
                "hasAccessToken", tokenResponse.accessToken() != null && !tokenResponse.accessToken().isBlank(),
                "hasRefreshToken", tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank(),
                "hasIdToken", tokenResponse.idToken() != null && !tokenResponse.idToken().isBlank()
        );
    }
}