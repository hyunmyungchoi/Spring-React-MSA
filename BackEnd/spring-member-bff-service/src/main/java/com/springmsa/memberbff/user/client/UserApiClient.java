package com.springmsa.memberbff.user.client;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.user.dto.CurrentUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "member-user-api-client", url = "${bff.api.user-api-base-url}")
public interface UserApiClient {

    @GetMapping("/api/user/me")
    MsaResponse<CurrentUserResponse> me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
