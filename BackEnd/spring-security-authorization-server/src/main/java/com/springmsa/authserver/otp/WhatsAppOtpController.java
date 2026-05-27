package com.springmsa.authserver.otp;

import com.springmsa.authserver.client.UserServiceClient;
import com.springmsa.authserver.client.dto.AuthUserResponse;
import com.springmsa.authserver.otp.common.OtpLoginSessionService;
import com.springmsa.authserver.otp.dto.OtpSendResponse;
import com.springmsa.authserver.otp.dto.OtpUserResponse;
import com.springmsa.authserver.otp.dto.OtpVerifyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@NullMarked
@RestController
@RequestMapping("/login/whatsapp")
public class WhatsAppOtpController {

    private final WhatsAppOtpService whatsAppOtpService;
    private final UserServiceClient userServiceClient;
    private final OtpLoginSessionService otpLoginSessionService;

    @Value("${app.otp.expose-dev-otp:false}")
    private boolean exposeDevOtp;

    public WhatsAppOtpController(
            WhatsAppOtpService whatsAppOtpService,
            UserServiceClient userServiceClient,
             OtpLoginSessionService otpLoginSessionService
    ) {
        this.whatsAppOtpService = whatsAppOtpService;
        this.userServiceClient = userServiceClient;
        this.otpLoginSessionService = otpLoginSessionService;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<OtpSendResponse> sendOtp(@Valid @RequestBody WhatsAppOtpSendRequest request) {
        String otp = whatsAppOtpService.issueOtp(request.whatsappNumber());

        OtpSendResponse response = exposeDevOtp
                ? OtpSendResponse.withDevOtp(whatsAppOtpService.getExpiresInSeconds(), otp)
                : OtpSendResponse.withoutDevOtp(whatsAppOtpService.getExpiresInSeconds());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpVerifyResponse> verify(
            @Valid @RequestBody WhatsAppOtpVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        boolean verified = whatsAppOtpService.verifyOtp(
                request.whatsappNumber(),
                request.otp()
        );

        if (!verified) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid or expired OTP"
            );
        }

        AuthUserResponse user = userServiceClient.findAuthUserByWhatsappNumber(
                request.whatsappNumber()
        );

        String redirectUrl = otpLoginSessionService.login(user, httpRequest, httpResponse);

        OtpVerifyResponse response = new OtpVerifyResponse(
                true,
                true,
                redirectUrl,
                OtpUserResponse.from(user)
        );

        return ResponseEntity.ok(response);
    }

}