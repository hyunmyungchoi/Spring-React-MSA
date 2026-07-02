package com.springmsa.authserver.client;

import com.springmsa.authserver.client.dto.AuthUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-user-service-client", url = "${app.user-service.base-url}")
public interface UserServiceFeignClient {

    @GetMapping("/internal/auth/users/{loginId}")
    AuthUserResponse findAuthUserByLoginId(@PathVariable String loginId);

    @GetMapping("/internal/auth/users/email/{email}")
    AuthUserResponse findAuthUserByEmail(@PathVariable String email);
}
