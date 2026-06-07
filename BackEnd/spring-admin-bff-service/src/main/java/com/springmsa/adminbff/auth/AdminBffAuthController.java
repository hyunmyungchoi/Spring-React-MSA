package com.springmsa.adminbff.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class AdminBffAuthController {

    private static final String SESSION_OAUTH2_STATE = "ADMIN_OAUTH2_STATE";

    private final AdminBffTokenService adminBffTokenService;
    private final AdminJwtRoleValidator adminJwtRoleValidator;
    private final AdminJwtClaimReader adminJwtClaimReader;

    @Value("${admin-bff.oauth2.authorization-uri}")
    private String authorizationUri;

    @Value("${admin-bff.oauth2.client-id}")
    private String clientId;

    @Value("${admin-bff.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${admin-bff.oauth2.scope}")
    private String scope;

    @Value("${admin-bff.oauth2.end-session-uri}")
    private String endSessionUri;

    @Value("${admin-bff.frontend.redirect-uri}")
    private String frontendRedirectUri;

    public AdminBffAuthController(AdminBffTokenService adminBffTokenService, AdminJwtRoleValidator adminJwtRoleValidator, AdminJwtClaimReader adminJwtClaimReader) {
        this.adminBffTokenService = adminBffTokenService;
        this.adminJwtRoleValidator = adminJwtRoleValidator;
        this.adminJwtClaimReader = adminJwtClaimReader;
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
    public RedirectView callback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpSession session
    ) {
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

        boolean admin = adminJwtRoleValidator.hasRole(
                tokenResponse.accessToken(),
                "ROLE_ADMIN"
        );

        if (!admin) {
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
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        Object accessTokenObj = session.getAttribute(AdminBffTokenService.SESSION_ACCESS_TOKEN);

        if (!(accessTokenObj instanceof String accessToken) || accessToken.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "user", null
            ));
        }

        Map<?, ?> claims = adminJwtClaimReader.readClaims(accessToken);

        boolean admin = adminJwtRoleValidator.hasRole(accessToken, "ROLE_ADMIN");

        if (!admin) {
            session.invalidate();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("authenticated", false);
            response.put("reason", "admin_role_required");

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticated", false);
        response.put("user", null);

        return ResponseEntity.ok(response);
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
                .queryParam("post_logout_redirect_uri", frontendRedirectUri)
                .build()
                .encode()
                .toUriString();

        return ResponseEntity.ok(Map.of(
                "logout", "success",
                "authServerLogoutRequired", true,
                "authServerLogoutUrl", authServerLogoutUrl
        ));
    }
}