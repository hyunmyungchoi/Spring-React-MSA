package com.springmsa.bff.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@RestController
public class BffAuthController {

    private static final String SESSION_OAUTH2_STATE = "OAUTH2_STATE";
    private static final String SESSION_ACCESS_TOKEN = "ACCESS_TOKEN";
    private static final String SESSION_REFRESH_TOKEN = "REFRESH_TOKEN";
    private static final String SESSION_ID_TOKEN = "ID_TOKEN";

    private final RestClient restClient;

    @Value("${bff.oauth2.authorization-uri}")
    private String authorizationUri;

    @Value("${bff.oauth2.token-uri}")
    private String tokenUri;

    @Value("${bff.oauth2.client-id}")
    private String clientId;

    @Value("${bff.oauth2.client-secret}")
    private String clientSecret;

    @Value("${bff.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${bff.oauth2.scope}")
    private String scope;

    public BffAuthController(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
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

    @GetMapping("/bff/auth/callback")
    public Map<String, Object> callback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpSession session
    ) {
        String savedState = (String) session.getAttribute(SESSION_OAUTH2_STATE);

        if (savedState != null && state != null && !savedState.equals(state)) {
            throw new IllegalArgumentException("Invalid OAuth2 state");
        }

        OAuth2TokenResponse tokenResponse = requestToken(code);

        session.setAttribute(SESSION_ACCESS_TOKEN, tokenResponse.accessToken());
        session.setAttribute(SESSION_REFRESH_TOKEN, tokenResponse.refreshToken());
        session.setAttribute(SESSION_ID_TOKEN, tokenResponse.idToken());

        return Map.of(
                "login", "success",
                "tokenType", tokenResponse.tokenType(),
                "expiresIn", tokenResponse.expiresIn(),
                "hasAccessToken", tokenResponse.accessToken() != null,
                "hasRefreshToken", tokenResponse.refreshToken() != null,
                "hasIdToken", tokenResponse.idToken() != null
        );
    }

    @GetMapping("/bff/auth/session")
    public Map<String, Object> session(HttpSession session) {
        return Map.of(
                "hasAccessToken", session.getAttribute(SESSION_ACCESS_TOKEN) != null,
                "hasRefreshToken", session.getAttribute(SESSION_REFRESH_TOKEN) != null,
                "hasIdToken", session.getAttribute(SESSION_ID_TOKEN) != null
        );
    }

    private OAuth2TokenResponse requestToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        return restClient.post()
                .uri(tokenUri)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(OAuth2TokenResponse.class);
    }
}