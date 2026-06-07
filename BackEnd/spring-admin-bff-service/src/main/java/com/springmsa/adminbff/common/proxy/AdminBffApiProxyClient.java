package com.springmsa.adminbff.common.proxy;

import com.springmsa.adminbff.auth.AdminBffTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AdminBffApiProxyClient {

    private final RestClient restClient;
    private final AdminBffTokenService adminBffTokenService;

    public AdminBffApiProxyClient(
            RestClient.Builder restClientBuilder,
            AdminBffTokenService adminBffTokenService
    ) {
        this.restClient = restClientBuilder.build();
        this.adminBffTokenService = adminBffTokenService;
    }

    public String get(HttpSession session, String uri) {
        String accessToken = adminBffTokenService.getAccessTokenOrThrow(session);

        return restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(String.class);
    }
}