package com.springmsa.bff.auth;

import com.springmsa.bff.auth.dto.AuthMeResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@NullMarked
@RestController
public class BffAuthController {

    private static final String SESSION_OAUTH2_STATE = "OAUTH2_STATE";
    private static final String SESSION_ACCESS_TOKEN = "ACCESS_TOKEN";
    private static final String SESSION_REFRESH_TOKEN = "REFRESH_TOKEN";
    private static final String SESSION_ID_TOKEN = "ID_TOKEN";

    private final BffTokenService bffTokenService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${bff.oauth2.authorization-uri}")
    private String authorizationUri;

    @Value("${bff.oauth2.client-id}")
    private String clientId;

    @Value("${bff.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${bff.oauth2.end-session-uri}")
    private String endSessionUri;

    @Value("${bff.oauth2.logout-uri}")
    private String logoutUri;

    @Value("${bff.oauth2.scope}")
    private String scope;

    @Value("${bff.oauth2.userinfo-uri}")
    private String userInfoUri;

    @Value("${bff.frontend.redirect-uri}")
    private String frontendRedirectUri;

    @Value("${bff.api.user-signup-uri:http://localhost:8081/internal/users}")
    private String userSignupUri;


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
                .encode()
                .toUriString();

        return new RedirectView(authorizeUrl);
    }

    @GetMapping("/bff/auth/callback")
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

        OAuth2TokenResponse tokenResponse = bffTokenService.exchangeCodeForToken(code);

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

        bffTokenService.saveTokenResponse(session, tokenResponse);

        return new RedirectView(frontendRedirectUri);
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

    @PostMapping("/bff/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        Object idTokenObj = session.getAttribute(BffTokenService.SESSION_ID_TOKEN);

        String idToken = idTokenObj instanceof String token ? token : "";

        session.invalidate();

        String postLogoutRedirectUri = frontendRedirectUri + "/login";

        String authServerLogoutUrl = StringUtils.hasText(idToken)
                ? UriComponentsBuilder.fromUriString(endSessionUri)
                        .queryParam("id_token_hint", idToken)
                        .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                        .build()
                        .encode()
                        .toUriString()
                : UriComponentsBuilder.fromUriString(logoutUri)
                        .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                        .build()
                        .encode()
                        .toUriString();

        return ResponseEntity.ok(Map.of(
                "logout", "success",
                "authServerLogoutRequired", true,
                "authServerLogoutUrl", authServerLogoutUrl
        ));
    }


    @GetMapping("/bff/auth/session")
    public Map<String, Object> session(HttpSession session) {
        return Map.of(
                "hasAccessToken", session.getAttribute(SESSION_ACCESS_TOKEN) != null,
                "hasRefreshToken", session.getAttribute(SESSION_REFRESH_TOKEN) != null,
                "hasIdToken", session.getAttribute(SESSION_ID_TOKEN) != null
        );
    }

    @PostMapping("/bff/auth/signup")
    public ResponseEntity<Object> signup(@RequestBody SignupRequest request) {
        SignupRequest signupRequest = request.withRoles(Set.of("ROLE_USER"));

        try {
            Object responseBody = restClient.post()
                    .uri(userSignupUri)
                    .body(signupRequest)
                    .retrieve()
                    .body(Object.class);

            return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);

        } catch (RestClientResponseException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(Map.of(
                            "success", false,
                            "status", e.getStatusCode().value(),
                            "message", resolveSignupErrorMessage(e)
                    ));
        }
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

    private AuthMeResponse buildAuthMeResponse(String accessToken) {
        Map<String, Object> userInfo = requestUserInfoMap(accessToken);
        return AuthMeResponse.authenticated(userInfo);
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
                    "Failed to load authentication userinfo",
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

    private String resolveSignupErrorMessage(RestClientResponseException e) {
        String responseBody = e.getResponseBodyAsString();

        if (StringUtils.hasText(responseBody)) {
            return responseBody;
        }

        return "Signup request failed";
    }

    public record SignupRequest(
            String loginId,
            String email,
            String password,
            String username,
            String phoneNumber,
            String whatsappNumber,
            Set<String> roles
    ) {
        SignupRequest withRoles(Set<String> nextRoles) {
            return new SignupRequest(
                    loginId,
                    email,
                    password,
                    username,
                    phoneNumber,
                    whatsappNumber,
                    nextRoles
            );
        }
    }




}
