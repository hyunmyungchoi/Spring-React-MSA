package com.springmsa.authserver.login.controller;

import com.springmsa.authserver.common.error.ErrorCode;
import com.springmsa.authserver.login.dto.LoginUserResponse;
import com.springmsa.authserver.login.dto.PasswordLoginRequest;
import com.springmsa.authserver.login.dto.PasswordLoginResponse;
import com.springmsa.authserver.login.service.LoginSessionService;
import com.springmsa.authserver.login.service.LoginUserQueryService;
import com.springmsa.authserver.user.dto.AuthUserResponse;
import com.springmsa.common.web.error.ApiException;
import com.springmsa.common.web.response.MsaResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/login/password")
@RequiredArgsConstructor
public class PasswordLoginController {

    private final LoginSessionService loginSessionService;
    private final LoginUserQueryService loginUserQueryService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping
    public ResponseEntity<MsaResponse<PasswordLoginResponse>> login(@Valid @RequestBody PasswordLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthUserResponse user = loginUserQueryService.findForPasswordLogin(request.loginId().trim());

        if (!user.enabled() || !passwordEncoder.matches(request.password(), user.password())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        String redirectUrl = loginSessionService.loginWithPassword(user, httpRequest, httpResponse);

        PasswordLoginResponse response = new PasswordLoginResponse(
                true,
                redirectUrl,
                LoginUserResponse.from(user)
        );

        return ResponseEntity.ok(MsaResponse.ok(response));
    }
}
