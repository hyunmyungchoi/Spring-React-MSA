package com.springmsa.bff.common.proxy;

import com.springmsa.bff.auth.BffTokenService;
import com.springmsa.bff.auth.OAuth2TokenResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@NullMarked
@Component
public class BffApiProxyClient {

    private final BffTokenService bffTokenService;
    private final RestClient restClient;

    public BffApiProxyClient(
            BffTokenService bffTokenService,
            RestClient.Builder restClientBuilder
    ) {
        this.bffTokenService = bffTokenService;
        this.restClient = restClientBuilder.build();
    }

    public String get(HttpSession session, String uri) {
        String accessToken = bffTokenService.getAccessTokenOrThrow(session);

        try {
            return requestGet(uri, accessToken);

        } catch (HttpClientErrorException.Unauthorized e) {
            OAuth2TokenResponse refreshedToken =
                    bffTokenService.refreshAccessToken(session);

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