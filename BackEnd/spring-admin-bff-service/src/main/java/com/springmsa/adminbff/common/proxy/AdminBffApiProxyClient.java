package com.springmsa.adminbff.common.proxy;

import com.springmsa.adminbff.auth.AdminBffTokenService;
import com.springmsa.adminbff.auth.OAuth2TokenResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Component
public class AdminBffApiProxyClient {

    private final AdminBffTokenService adminBffTokenService;
    private final RestClient restClient;

    public AdminBffApiProxyClient(
            AdminBffTokenService adminBffTokenService,
            RestClient.Builder restClientBuilder
    ) {
        this.adminBffTokenService = adminBffTokenService;
        this.restClient = restClientBuilder.build();
    }

    public String get(HttpSession session, String uri) {
        String accessToken = adminBffTokenService.getAccessTokenOrThrow(session);

        try {
            return requestGet(uri, accessToken);

        } catch (HttpClientErrorException.Unauthorized e) {
            OAuth2TokenResponse refreshedToken =
                    adminBffTokenService.refreshAccessToken(session);

            return requestGet(uri, refreshedToken.accessToken());
        }
    }

    private String requestGet(String uri, String accessToken) {
        return Objects.requireNonNull(
                restClient.get()
                        .uri(uri)
                        .headers(headers -> headers.setBearerAuth(accessToken))
                        .retrieve()
                        .body(String.class)
        );
    }
}