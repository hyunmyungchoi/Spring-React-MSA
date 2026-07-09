package com.springmsa.userservice.user.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.userservice.user.dto.CurrentUserResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class CurrentUserApiController {

    @GetMapping("/me")
    public MsaResponse<CurrentUserResponse> me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();

        CurrentUserResponse response = new CurrentUserResponse(
                jwt.getSubject(),
                jwt.getClaim("user_id"),
                jwt.getClaim("login_id"),
                jwt.getClaim("email"),
                jwt.getClaimAsStringList("roles")
        );

        return MsaResponse.ok(response);
    }
}
