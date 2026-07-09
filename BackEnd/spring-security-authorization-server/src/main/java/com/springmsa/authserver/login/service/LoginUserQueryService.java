package com.springmsa.authserver.login.service;

import com.springmsa.authserver.common.error.ErrorCode;
import com.springmsa.authserver.user.client.UserServiceClient;
import com.springmsa.authserver.user.dto.AuthUserResponse;
import com.springmsa.common.web.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LoginUserQueryService {

    private final UserServiceClient userServiceClient;

    public AuthUserResponse findForPasswordLogin(String loginId) {
        try {
            return userServiceClient.findAuthUserByLoginId(loginId);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, exception);
            }

            throw new ApiException(ErrorCode.USER_SERVICE_ERROR, exception);
        }
    }
}
