package com.springmsa.authserver.login;

import com.springmsa.authserver.client.UserServiceClient;
import com.springmsa.authserver.client.dto.AuthUserResponse;
import com.springmsa.authserver.login.dto.LoginUserResponse;
import com.springmsa.authserver.login.dto.PasswordLoginRequest;
import com.springmsa.authserver.login.dto.PasswordLoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/login/password")
public class PasswordLoginController {

    private final UserServiceClient userServiceClient;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessionService loginSessionService;

    public PasswordLoginController(
            UserServiceClient userServiceClient,
            PasswordEncoder passwordEncoder,
            LoginSessionService loginSessionService
    ) {
        this.userServiceClient = userServiceClient;
        this.passwordEncoder = passwordEncoder;
        this.loginSessionService = loginSessionService;
    }

    @PostMapping
    public ResponseEntity<PasswordLoginResponse> login(
            @Valid @RequestBody PasswordLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthUserResponse user = userServiceClient.findAuthUserByLoginId(request.loginId().trim());

        if (!user.enabled() || !passwordEncoder.matches(request.password(), user.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login ID or password");
        }

        String redirectUrl = loginSessionService.loginWithPassword(user, httpRequest, httpResponse);

        return ResponseEntity.ok(new PasswordLoginResponse(
                true,
                redirectUrl,
                LoginUserResponse.from(user)
        ));
    }
}
