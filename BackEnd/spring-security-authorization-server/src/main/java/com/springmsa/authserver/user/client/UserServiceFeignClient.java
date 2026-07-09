package com.springmsa.authserver.user.client;

import com.springmsa.authserver.user.dto.AuthUserResponse;
import com.springmsa.common.web.response.MsaResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "auth-user-service-client",
        url = "${app.user-service.base-url}",
        configuration = UserServiceInternalApiFeignConfig.class
)
public interface UserServiceFeignClient {

    @GetMapping("/internal/auth/users/{loginId}")
    MsaResponse<AuthUserResponse> findAuthUserByLoginId(@PathVariable String loginId);

    @GetMapping("/internal/auth/users/email/{email}")
    MsaResponse<AuthUserResponse> findAuthUserByEmail(@PathVariable String email);
}
