package com.springmsa.memberbff.user;

import com.springmsa.memberbff.auth.BffOAuth2ClientService;
import com.springmsa.memberbff.client.UserApiClient;
import com.springmsa.memberbff.user.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final BffOAuth2ClientService bffOAuth2ClientService;
    private final UserApiClient userApiClient;

    public CurrentUserResponse getCurrentUser(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return userApiClient.me(bearerToken(authentication, request, response));
    }

    private String bearerToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return "Bearer " + bffOAuth2ClientService.getAccessToken(authentication, request, response);
    }
}
