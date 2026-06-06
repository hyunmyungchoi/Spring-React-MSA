package com.springmsa.adminbff.auth;

import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@NullMarked
@Service
public class AdminBffTokenService {

    public static final String SESSION_ACCESS_TOKEN = "ADMIN_ACCESS_TOKEN";
    public static final String SESSION_REFRESH_TOKEN = "ADMIN_REFRESH_TOKEN";
    public static final String SESSION_ID_TOKEN = "ADMIN_ID_TOKEN";

    private final RestClient restClient;

    @Value("${admin-bff.oauth2.token-uri}")
    private String tokenUri;

    @Value("${admin-bff.oauth2.client-id}")
    private String clientId;

    @Value("${admin-bff.oauth2.client-secret}")
    private String clientSecret;

    @Value("${admin-bff.oauth2.redirect-uri}")
    private String redirectUri;

    public AdminBffTokenService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String getAccessTokenOrThrow(HttpSession session) {
        String accessToken = (String) session.getAttribute(SESSION_ACCESS_TOKEN);

        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin BFF session has no access token");
        }

        return accessToken;
    }

    public OAuth2TokenResponse exchangeCodeForToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        return Objects.requireNonNull(restClient.post()
                .uri(tokenUri)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(OAuth2TokenResponse.class));
    }

    public OAuth2TokenResponse refreshAccessToken(HttpSession session) {
        String refreshToken = (String) session.getAttribute(SESSION_REFRESH_TOKEN);

        if (refreshToken == null || refreshToken.isBlank()) {
            session.invalidate();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin BFF session has no refresh token");
        }

        try {
            OAuth2TokenResponse tokenResponse = requestTokenByRefreshToken(refreshToken);
            saveTokenResponse(session, tokenResponse);
            return tokenResponse;

        } catch (HttpClientErrorException e) {
            session.invalidate();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin refresh token is invalid or expired");
        }
    }

    public void saveTokenResponse(HttpSession session, OAuth2TokenResponse tokenResponse) {
        if (tokenResponse.accessToken() != null && !tokenResponse.accessToken().isBlank()) {
            session.setAttribute(SESSION_ACCESS_TOKEN, tokenResponse.accessToken());
        }

        if (tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank()) {
            session.setAttribute(SESSION_REFRESH_TOKEN, tokenResponse.refreshToken());
        }

        if (tokenResponse.idToken() != null && !tokenResponse.idToken().isBlank()) {
            session.setAttribute(SESSION_ID_TOKEN, tokenResponse.idToken());
        }
    }

    private OAuth2TokenResponse requestTokenByRefreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        return Objects.requireNonNull(restClient.post()
                .uri(tokenUri)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(OAuth2TokenResponse.class));
    }
}