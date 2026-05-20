package com.springmsa.bff.user.me;

import com.springmsa.bff.auth.BffTokenService;
import com.springmsa.bff.auth.OAuth2TokenResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@NullMarked
@RestController
public class BffUserController {

    private final BffTokenService bffTokenService;
    private final RestClient restClient;

    @Value("${bff.api.user-me-uri}")
    private String userMeUri;

    public BffUserController(BffTokenService bffTokenService, RestClient.Builder restClientBuilder) {
        this.bffTokenService = bffTokenService;
        this.restClient = restClientBuilder.build();
    }

    @GetMapping("/bff/user/me")
    public ResponseEntity<String> me(HttpSession session) {
        String accessToken = bffTokenService.getAccessTokenOrThrow(session);

        try {
            String responseBody = requestUserMe(accessToken);
            return ResponseEntity.ok(responseBody);

        } catch (HttpClientErrorException.Unauthorized e) {
            OAuth2TokenResponse refreshedToken = bffTokenService.refreshAccessToken(session);
            bffTokenService.saveTokenResponse(session, refreshedToken);

            String responseBody = requestUserMe(refreshedToken.accessToken());
            return ResponseEntity.ok(responseBody);
        }
    }

    private String requestUserMe(String accessToken) {
        return Objects.requireNonNull(restClient.get()
                .uri(userMeUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class));
    }
}
