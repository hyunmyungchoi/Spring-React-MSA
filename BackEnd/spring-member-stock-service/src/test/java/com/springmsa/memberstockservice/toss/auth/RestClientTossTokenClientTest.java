package com.springmsa.memberstockservice.toss.auth;

import com.springmsa.memberstockservice.toss.config.TossApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientTossTokenClientTest {

    @Test
    void issuesFormUrlEncodedClientCredentialsRequest() {
        TossApiProperties properties = new TossApiProperties(
                "https://openapi.tossinvest.com",
                "dummy-client-id",
                "dummy-client-secret",
                "toss:oauth:access-token",
                "toss:oauth:refresh-lock"
        );
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientTossTokenClient client = new RestClientTossTokenClient(builder, properties);

        server.expect(requestTo("https://openapi.tossinvest.com/oauth2/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=client_credentials"),
                        org.hamcrest.Matchers.containsString("client_id=dummy-client-id"),
                        org.hamcrest.Matchers.containsString("client_secret=dummy-client-secret")
                )))
                .andRespond(withSuccess("""
                        {
                          "access_token": "issued-token",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));

        TossTokenResponse response = client.issueToken();

        assertThat(response.accessToken()).isEqualTo("issued-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        server.verify();
    }
}
