package com.springmsa.memberstockservice.toss.auth;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.toss.config.TossApiProperties;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FeignTossTokenClient implements TossTokenClient {

    private static final String CLIENT_CREDENTIALS = "client_credentials";

    private final TossTokenFeignClient feignClient;
    private final TossApiProperties properties;

    @Override
    public TossTokenResponse issueToken() {
        try {
            return feignClient.issueToken(
                    CLIENT_CREDENTIALS,
                    properties.clientId(),
                    properties.clientSecret()
            );
        } catch (FeignException exception) {
            throw new ApiException(TossErrorCode.TOSS_TOKEN_UNAVAILABLE, exception);
        }
    }
}
