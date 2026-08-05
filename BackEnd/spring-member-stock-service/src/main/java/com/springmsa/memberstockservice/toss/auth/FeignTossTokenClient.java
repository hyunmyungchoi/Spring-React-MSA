package com.springmsa.memberstockservice.toss.auth;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.toss.config.TossApiProperties;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
@RequiredArgsConstructor
class FeignTossTokenClient implements TossTokenClient {

    private static final String CLIENT_CREDENTIALS = "client_credentials";

    private final TossTokenFeignClient feignClient;
    private final TossApiProperties properties;

    @Override
    public TossTokenResponse issueToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", CLIENT_CREDENTIALS);
        formData.add("client_id", properties.clientId());
        formData.add("client_secret", properties.clientSecret());

        try {
            return feignClient.issueToken(formData);
        } catch (FeignException exception) {
            throw new ApiException(TossErrorCode.TOSS_TOKEN_UNAVAILABLE, exception);
        }
    }
}
