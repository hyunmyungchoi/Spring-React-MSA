package com.springmsa.memberbff.client;

import com.springmsa.memberbff.client.dto.UserCreateRequest;
import com.springmsa.memberbff.client.dto.UserCreateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "member-user-internal-client", url = "${bff.api.user-internal-base-url}")
public interface UserInternalClient {

    @PostMapping("/internal/users")
    UserCreateResponse create(@RequestBody UserCreateRequest request);
}
