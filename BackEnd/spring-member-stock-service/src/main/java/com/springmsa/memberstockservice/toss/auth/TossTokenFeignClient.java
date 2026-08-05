package com.springmsa.memberstockservice.toss.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "toss-token-feign-client", url = "${toss.api.base-url}")
public interface TossTokenFeignClient {

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    TossTokenResponse issueToken(@RequestBody MultiValueMap<String, String> formData);
}
