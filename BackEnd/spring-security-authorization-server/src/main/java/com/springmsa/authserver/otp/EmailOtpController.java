package com.springmsa.authserver.otp;

import com.springmsa.authserver.client.UserServiceClient;
import com.springmsa.authserver.client.dto.AuthUserResponse;
import com.springmsa.authserver.otp.common.OtpLoginSessionService;
import com.springmsa.authserver.otp.email.EmailOtpSender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@NullMarked
@RestController
@RequestMapping("/login/email")
public class EmailOtpController {

    private final EmailOtpService emailOtpService;
    private final UserServiceClient userServiceClient;
    private final OtpLoginSessionService otpLoginSessionService;
    private final EmailOtpSender emailOtpSender;

    @Value("${app.otp.expose-dev-otp:false}")
    private boolean exposeDevOtp;

    public EmailOtpController(
            EmailOtpService emailOtpService,
            UserServiceClient userServiceClient,
            OtpLoginSessionService otpLoginSessionService, EmailOtpSender emailOtpSender
    ) {
        this.emailOtpService = emailOtpService;
        this.userServiceClient = userServiceClient;
        this.otpLoginSessionService = otpLoginSessionService;
        this.emailOtpSender = emailOtpSender;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@Valid @RequestBody EmailOtpSendRequest request) {
        String otp = emailOtpService.issueOtp(request.email());

        emailOtpSender.sendOtp(request.email(), otp);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sent", true);
        response.put("expiresInSeconds", 180);

        if (exposeDevOtp) {
            response.put("devOtp", otp);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @Valid @RequestBody EmailOtpVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        boolean verified = emailOtpService.verifyOtp(request.email(), request.otp());

        if (!verified) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("verified", false);
            response.put("message", "Invalid or expired OTP");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        AuthUserResponse user = userServiceClient.findAuthUserByEmail(request.email());

        String redirectUrl = otpLoginSessionService.login(user, httpRequest, httpResponse);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("verified", true);
        response.put("authenticated", true);

        if (redirectUrl != null) {
            response.put("redirectUrl", redirectUrl);
        }

        response.put("user", Map.of(
                "userId", user.userId(),
                "loginId", user.loginId(),
                "email", user.email(),
                "username", user.username(),
                "roles", user.roles()
        ));

        return ResponseEntity.ok(response);
    }

}