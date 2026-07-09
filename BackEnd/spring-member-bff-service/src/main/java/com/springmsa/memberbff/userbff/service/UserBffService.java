package com.springmsa.memberbff.userbff.service;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.auth.service.BffOAuth2ClientService;
import com.springmsa.memberbff.userbff.client.UserServiceClient;
import com.springmsa.memberbff.userbff.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserBffService {

    private final BffOAuth2ClientService bffOAuth2ClientService;
    private final UserServiceClient userServiceClient;

    public CurrentUserResponse getCurrentUser(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return requireData(userServiceClient.me(bearerToken(authentication, request, response)));
    }

    private String bearerToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return "Bearer " + bffOAuth2ClientService.getAccessToken(authentication, request, response);
    }

    private CurrentUserResponse requireData(MsaResponse<CurrentUserResponse> response) {
        if (response == null || response.data() == null) {
            throw new IllegalStateException("User service returned an empty current user response");
        }

        return response.data();
    }
}
