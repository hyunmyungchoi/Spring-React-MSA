package com.springmsa.memberbff.registration.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

public class UserInternalClientConfig {

    @Bean
    RequestInterceptor internalApiTokenRequestInterceptor(
            @Value("${springmsa.internal-api.header-name:X-Internal-Token}") String headerName,
            @Value("${springmsa.internal-api.token}") String token
    ) {
        if (!StringUtils.hasText(headerName)) {
            throw new IllegalStateException("springmsa.internal-api.header-name must not be blank");
        }

        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("springmsa.internal-api.token must not be blank");
        }

        return template -> template.header(headerName, token);
    }
}
