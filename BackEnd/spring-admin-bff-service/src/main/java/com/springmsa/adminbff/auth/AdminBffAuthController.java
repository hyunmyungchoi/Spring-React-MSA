package com.springmsa.adminbff.auth;

import com.springmsa.adminbff.auth.dto.AdminAuthMeResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


@RestController
public class AdminBffAuthController {

    private static final String SESSION_OAUTH2_STATE = "ADMIN_OAUTH2_STATE";

    private final AdminBffTokenService adminBffTokenService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${admin-bff.oauth2.authorization-uri}")
    private String authorizationUri;

    @Value("${admin-bff.oauth2.client-id}")
    private String clientId;

    @Value("${admin-bff.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${admin-bff.oauth2.end-session-uri}")
    private String endSessionUri;

    @Value("${admin-bff.oauth2.scope}")
    private String scope;

    @Value("${admin-bff.oauth2.userinfo-uri}")
    private String userInfoUri;

    @Value("${admin-bff.frontend.redirect-uri}")
    private String frontendRedirectUri;

    private static final String ROLE_ADMIN = "ROLE_ADMIN";


    public AdminBffAuthController(AdminBffTokenService adminBffTokenService, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.adminBffTokenService = adminBffTokenService;
        this.objectMapper = objectMapper;
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
    public RedirectView callback(@RequestParam String code, @RequestParam(required = false) String state, HttpSession session) {
        Object savedState = session.getAttribute(SESSION_OAUTH2_STATE);

        if (!(savedState instanceof String savedStateValue) || !savedStateValue.equals(state)) {
            return new RedirectView(
                    UriComponentsBuilder.fromUriString(frontendRedirectUri)
                            .queryParam("error", "invalid_state")
                            .build()
                            .encode()
                            .toUriString()
            );
        }

        session.removeAttribute(SESSION_OAUTH2_STATE);

        OAuth2TokenResponse tokenResponse = adminBffTokenService.exchangeCodeForToken(code);

        if (tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
            session.invalidate();

            return new RedirectView(
                    UriComponentsBuilder.fromUriString(frontendRedirectUri)
                            .queryParam("error", "invalid_token_response")
                            .build()
                            .encode()
                            .toUriString()
            );
        }

        Map<String, Object> userInfo = requestUserInfoMap(tokenResponse.accessToken());

        if (hasRole(userInfo, ROLE_ADMIN)) {
            session.invalidate();

            return new RedirectView(
                    UriComponentsBuilder.fromUriString(frontendRedirectUri)
                            .queryParam("error", "admin_role_required")
                            .build()
                            .encode()
                            .toUriString()
            );
        }

        adminBffTokenService.saveTokenResponse(session, tokenResponse);

        return new RedirectView(frontendRedirectUri);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<AdminAuthMeResponse> me(HttpSession session) {
        String accessToken;

        try {
            accessToken = adminBffTokenService.getAccessTokenOrThrow(session);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 401) {
                return ResponseEntity.ok(AdminAuthMeResponse.anonymous());
            }

            throw e;
        }

        if (accessToken.isBlank()) {
            return ResponseEntity.ok(AdminAuthMeResponse.anonymous());
        }

        try {
            Map<String, Object> userInfo = requestUserInfoMap(accessToken);

            if (hasRole(userInfo, ROLE_ADMIN)) {
                return ResponseEntity.ok(AdminAuthMeResponse.authenticated(userInfo));
            }

            session.invalidate();

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(AdminAuthMeResponse.adminRoleRequired());

        } catch (HttpClientErrorException.Unauthorized e) {
            try {
                OAuth2TokenResponse refreshedToken = adminBffTokenService.refreshAccessToken(session);
                Map<String, Object> userInfo = requestUserInfoMap(refreshedToken.accessToken());

                if (hasRole(userInfo, ROLE_ADMIN)) {
                    return ResponseEntity.ok(AdminAuthMeResponse.authenticated(userInfo));
                }

                session.invalidate();

                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(AdminAuthMeResponse.adminRoleRequired());

            } catch (ResponseStatusException refreshFail) {
                return ResponseEntity.ok(AdminAuthMeResponse.anonymous());
            }
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        Object idTokenObj = session.getAttribute(AdminBffTokenService.SESSION_ID_TOKEN);

        String idToken = idTokenObj instanceof String token ? token : "";

        session.invalidate();

        if (!StringUtils.hasText(idToken)) {
            return ResponseEntity.ok(Map.of(
                    "logout", "success",
                    "authServerLogoutRequired", false
            ));
        }

        String authServerLogoutUrl = UriComponentsBuilder.fromUriString(endSessionUri)
                .queryParam("id_token_hint", idToken)
                .queryParam("post_logout_redirect_uri", frontendRedirectUri+ "/login")
                .build()
                .encode()
                .toUriString();

        return ResponseEntity.ok(Map.of(
                "logout", "success",
                "authServerLogoutRequired", true,
                "authServerLogoutUrl", authServerLogoutUrl
        ));
    }

    private Map<String, Object> requestUserInfoMap(String accessToken) {
        try {
            String responseBody = requestUserInfo(accessToken);

            Object parsed = objectMapper.readValue(responseBody, Map.class);

            if (!(parsed instanceof Map<?, ?> parsedMap)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "OIDC userinfo response is not a JSON object"
                );
            }

            Map<String, Object> userInfo = new java.util.LinkedHashMap<>();

            parsedMap.forEach((key, value) ->
                    userInfo.put(String.valueOf(key), value)
            );

            return userInfo;

        } catch (ResponseStatusException e) {
            throw e;

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to load admin userinfo",
                    e
            );
        }
    }

    private String requestUserInfo(String accessToken) {
        return Objects.requireNonNull(
                restClient.get()
                        .uri(userInfoUri)
                        .headers(headers -> headers.setBearerAuth(accessToken))
                        .retrieve()
                        .body(String.class)
        );
    }

    private boolean hasRole(Map<String, Object> userInfo, String roleName) {
        Object roles = userInfo.get("roles");

        if (!(roles instanceof Collection<?> roleValues)) {
            return false;
        }

        return roleValues.stream()
                .map(String::valueOf)
                .anyMatch(roleName::equals);
    }
}