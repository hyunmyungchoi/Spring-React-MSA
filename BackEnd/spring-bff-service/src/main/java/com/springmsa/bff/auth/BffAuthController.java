package com.springmsa.bff.auth;

import com.springmsa.bff.auth.dto.AuthMeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@NullMarked
@RestController
public class BffAuthController {

    private static final String SESSION_OAUTH2_STATE = "OAUTH2_STATE";
    private static final String SESSION_ACCESS_TOKEN = "ACCESS_TOKEN";
    private static final String SESSION_REFRESH_TOKEN = "REFRESH_TOKEN";
    private static final String SESSION_ID_TOKEN = "ID_TOKEN";

    private final RestClient restClient;

    private final BffTokenService bffTokenService;

    @Value("${bff.oauth2.authorization-uri}")
    private String authorizationUri;

    @Value("${bff.oauth2.client-id}")
    private String clientId;

    @Value("${bff.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${bff.oauth2.scope}")
    private String scope;

    @Value("${bff.oauth2.userinfo-uri}")
    private String userInfoUri;

    @Value("${bff.frontend.redirect-uri}")
    private String frontendRedirectUri;

    private final ObjectMapper objectMapper;



    public BffAuthController(RestClient.Builder restClientBuilder, BffTokenService bffTokenService, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.bffTokenService = bffTokenService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/bff/auth/login")
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
                .toUriString();

        return new RedirectView(authorizeUrl);
    }

    @PostMapping("/bff/auth/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return Map.of(
                "logout", "success"
        );
    }


    @GetMapping("/bff/auth/callback")
    public RedirectView callback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpSession session
    ) {
        String savedState = (String) session.getAttribute(SESSION_OAUTH2_STATE);

        if (savedState == null || !savedState.equals(state)) {
            throw new IllegalArgumentException("Invalid OAuth2 state");
        }

        session.removeAttribute(SESSION_OAUTH2_STATE);

        OAuth2TokenResponse tokenResponse = bffTokenService.exchangeCodeForToken(code);
        bffTokenService.saveTokenResponse(session, tokenResponse);

        return new RedirectView(frontendRedirectUri);
    }

    @GetMapping("/bff/auth/session")
    public Map<String, Object> session(HttpSession session) {
        return Map.of(
                "hasAccessToken", session.getAttribute(SESSION_ACCESS_TOKEN) != null,
                "hasRefreshToken", session.getAttribute(SESSION_REFRESH_TOKEN) != null,
                "hasIdToken", session.getAttribute(SESSION_ID_TOKEN) != null
        );
    }

    @PostMapping("/bff/auth/refresh")
    public Map<String, Object> refresh(HttpSession session) {
        OAuth2TokenResponse tokenResponse = bffTokenService.refreshAccessToken(session);
        return Map.of(
                "refresh", "success",
                "tokenType", tokenResponse.tokenType(),
                "expiresIn", tokenResponse.expiresIn(),
                "hasAccessToken", tokenResponse.accessToken() != null,
                "hasRefreshToken", session.getAttribute(SESSION_REFRESH_TOKEN) != null,
                "hasIdToken", tokenResponse.idToken() != null
        );
    }


    @GetMapping("/bff/auth/me")
    public ResponseEntity<AuthMeResponse> me(HttpSession session) {
        String accessToken;

        try {
            accessToken = bffTokenService.getAccessTokenOrThrow(session);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 401) {
                return ResponseEntity.ok(AuthMeResponse.anonymous());
            }

            throw e;
        }

        if (accessToken.isBlank()) {
            return ResponseEntity.ok(AuthMeResponse.anonymous());
        }

        try {
            return ResponseEntity.ok(buildAuthMeResponse(accessToken));

        } catch (HttpClientErrorException.Unauthorized e) {
            try {
                OAuth2TokenResponse refreshedToken = bffTokenService.refreshAccessToken(session);
                return ResponseEntity.ok(buildAuthMeResponse(refreshedToken.accessToken()));

            } catch (ResponseStatusException refreshFail) {
                return ResponseEntity.ok(AuthMeResponse.anonymous());
            }
        }
    }

    private String requestUserInfo(String accessToken) {
        return Objects.requireNonNull(restClient.get()
                .uri(userInfoUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class));
    }

    @SuppressWarnings("unchecked")
    private AuthMeResponse buildAuthMeResponse(String accessToken) {
        try {
            String responseBody = requestUserInfo(accessToken);

            Map<String, Object> userInfo = objectMapper.readValue(
                    responseBody,
                    Map.class
            );

            return AuthMeResponse.authenticated(userInfo);

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to load authentication user",
                    e
            );
        }
    }


}