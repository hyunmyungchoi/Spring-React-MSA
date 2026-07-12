package com.springmsa.memberstockservice.toss.auth;

import com.springmsa.memberstockservice.toss.config.TossApiProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public interface TossTokenClient {

    TossTokenResponse issueToken();
}

@Component
class RestClientTossTokenClient implements TossTokenClient {

    private final RestClient restClient;
    private final TossApiProperties properties;

    RestClientTossTokenClient(RestClient.Builder builder, TossApiProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public TossTokenResponse issueToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        return restClient.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TossTokenResponse.class);
    }
}
