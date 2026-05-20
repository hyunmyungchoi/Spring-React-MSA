package com.springmsa.bff.community;

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
public class BffCommunityController {

    private final RestClient restClient;

    private final BffTokenService bffTokenService;

    @Value("${bff.api.community-me-uri}")
    private String communityMeUri;

    public BffCommunityController(RestClient.Builder restClientBuilder, BffTokenService bffTokenService) {
        this.restClient = restClientBuilder.build();
        this.bffTokenService = bffTokenService;
    }

    @GetMapping("/bff/community/me")
    public ResponseEntity<String> me(HttpSession session) {
        String accessToken = bffTokenService.getAccessTokenOrThrow(session);

        try {
            String responseBody = callCommunityMe(accessToken);
            return ResponseEntity.ok(responseBody);

        } catch (HttpClientErrorException.Unauthorized e) {
            OAuth2TokenResponse refreshedToken = bffTokenService.refreshAccessToken(session);

            String responseBody = callCommunityMe(refreshedToken.accessToken());
            return ResponseEntity.ok(responseBody);
        }
    }


    private String callCommunityMe(String accessToken) {
        return Objects.requireNonNull(restClient.get()
                .uri(communityMeUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class));
    }

}