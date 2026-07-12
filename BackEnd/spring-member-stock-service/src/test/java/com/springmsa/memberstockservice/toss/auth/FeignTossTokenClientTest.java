package com.springmsa.memberstockservice.toss.auth;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.toss.config.TossApiProperties;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeignTossTokenClientTest {

    private final TossTokenFeignClient feignClient = mock(TossTokenFeignClient.class);

    private FeignTossTokenClient client;

    @BeforeEach
    void setUp() {
        TossApiProperties properties = new TossApiProperties(
                "https://openapi.tossinvest.com",
                "dummy-client-id",
                "dummy-client-secret",
                "toss:oauth:access-token",
                "toss:oauth:refresh-lock"
        );
        client = new FeignTossTokenClient(feignClient, properties);
    }

    @Test
    void issuesClientCredentialsTokenThroughFeignClient() {
        when(feignClient.issueToken("client_credentials", "dummy-client-id", "dummy-client-secret"))
                .thenReturn(new TossTokenResponse("issued-token", "Bearer", 3600));

        TossTokenResponse response = client.issueToken();

        assertThat(response.accessToken()).isEqualTo("issued-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    void mapsFeignFailureToTokenUnavailable() {
        when(feignClient.issueToken("client_credentials", "dummy-client-id", "dummy-client-secret"))
                .thenThrow(mock(FeignException.class));

        assertThatThrownBy(client::issueToken)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("TOSS_TOKEN_UNAVAILABLE");
                    assertThat(apiException.status().value()).isEqualTo(503);
                });
    }
}
