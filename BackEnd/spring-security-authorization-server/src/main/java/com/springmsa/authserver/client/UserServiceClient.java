package com.springmsa.authserver.client;

import com.springmsa.authserver.client.dto.AuthUserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserServiceClient {

    private final RestClient restClient;

    public UserServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.user-service.base-url}") String userServiceBaseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(userServiceBaseUrl)
                .build();
    }

    public AuthUserResponse findAuthUserByLoginId(String loginId) {
        return restClient.get()
                .uri("/internal/auth/users/{loginId}", loginId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new ResponseStatusException(response.getStatusCode(), "User not found");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new ResponseStatusException(response.getStatusCode(), "User service error");
                })
                .body(AuthUserResponse.class);
    }

    public AuthUserResponse findAuthUserByEmail(String email) {
        return restClient.get()
                .uri("/internal/auth/users/email/{email}", email)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new ResponseStatusException(response.getStatusCode(), "User not found by email");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new ResponseStatusException(response.getStatusCode(), "User service error");
                })
                .body(AuthUserResponse.class);
    }
}
