package com.springmsa.authserver.otp;

import com.springmsa.authserver.client.UserServiceClient;
import com.springmsa.authserver.client.dto.AuthUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;

import java.util.Map;
import java.util.Set;

@NullMarked
@RestController
@RequestMapping("/login/whatsapp")
public class WhatsAppOtpController {

    private final WhatsAppOtpService whatsAppOtpService;
    private final UserServiceClient userServiceClient;
    private final SecurityContextRepository securityContextRepository;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public WhatsAppOtpController(
            WhatsAppOtpService whatsAppOtpService,
            UserServiceClient userServiceClient, SecurityContextRepository securityContextRepository
    ) {
        this.whatsAppOtpService = whatsAppOtpService;
        this.userServiceClient = userServiceClient;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody WhatsAppOtpSendRequest request) {
        String otp = whatsAppOtpService.issueOtp(request.whatsappNumber());

        return ResponseEntity.ok(Map.of(
                "sent", true,
                "expiresInSeconds", 180,
                "devOtp", otp
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestBody WhatsAppOtpVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        boolean verified = whatsAppOtpService.verifyOtp(
                request.whatsappNumber(),
                request.otp()
        );

        if (!verified) {
            return ResponseEntity.ok(Map.of(
                    "verified", false
            ));
        }

        AuthUserResponse user = userServiceClient.findAuthUserByWhatsappNumber(
                request.whatsappNumber()
        );

        Authentication authentication = createAuthentication(user, httpRequest);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);

        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

        SavedRequest savedRequest = requestCache.getRequest(httpRequest, httpResponse);
        String redirectUrl = savedRequest != null ? savedRequest.getRedirectUrl() : "/";

        return ResponseEntity.ok(Map.of(
                "verified", true,
                "authenticated", true,
                "redirectUrl", redirectUrl,
                "user", Map.of(
                        "userId", user.userId(),
                        "loginId", user.loginId(),
                        "email", user.email(),
                        "username", user.username(),
                        "roles", user.roles()
                )
        ));
    }

    private Authentication createAuthentication(
            AuthUserResponse user,
            HttpServletRequest request
    ) {
        Set<String> roles = user.roles() == null ? Set.of() : user.roles();

        Collection<GrantedAuthority> authorities = new ArrayList<>();

        roles.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        authorities.add(
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.OTT_AUTHORITY)
        );

        UserDetails principal = User.withUsername(user.loginId())
                .password("")
                .authorities(authorities)
                .disabled(!user.enabled())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        return authentication;
    }
}