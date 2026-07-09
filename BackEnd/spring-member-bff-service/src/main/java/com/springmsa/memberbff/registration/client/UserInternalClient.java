package com.springmsa.memberbff.registration.client;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.registration.client.dto.UserCreateRequest;
import com.springmsa.memberbff.registration.client.dto.UserCreateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "member-user-internal-client",
        url = "${bff.api.user-internal-base-url}",
        configuration = UserInternalClientConfig.class
)
public interface UserInternalClient {

    @PostMapping("/internal/users")
    MsaResponse<UserCreateResponse> create(@RequestBody UserCreateRequest request);
}
