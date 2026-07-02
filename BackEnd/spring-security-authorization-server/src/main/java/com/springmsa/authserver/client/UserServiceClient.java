package com.springmsa.authserver.client;

import com.springmsa.authserver.client.dto.AuthUserResponse;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserServiceClient {

    private final UserServiceFeignClient userServiceFeignClient;

    public UserServiceClient(UserServiceFeignClient userServiceFeignClient) {
        this.userServiceFeignClient = userServiceFeignClient;
    }

    public AuthUserResponse findAuthUserByLoginId(String loginId) {
        try {
            return userServiceFeignClient.findAuthUserByLoginId(loginId);
        } catch (FeignException e) {
            throw toResponseStatusException(e, "User not found");
        }
    }

    public AuthUserResponse findAuthUserByEmail(String email) {
        try {
            return userServiceFeignClient.findAuthUserByEmail(email);
        } catch (FeignException e) {
            throw toResponseStatusException(e, "User not found by email");
        }
    }

    private ResponseStatusException toResponseStatusException(FeignException exception, String clientErrorReason) {
        int status = exception.status();

        if (status >= 400 && status < 500) {
            return new ResponseStatusException(HttpStatusCode.valueOf(status), clientErrorReason, exception);
        }

        if (status >= 500 && status < 600) {
            return new ResponseStatusException(HttpStatusCode.valueOf(status), "User service error", exception);
        }

        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User service error", exception);
    }
}
