package com.springmsa.memberbff.auth.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.auth.dto.AuthMeResponse;
import com.springmsa.memberbff.auth.dto.LogoutResponse;
import com.springmsa.memberbff.auth.service.BffAuthenticationService;
import com.springmsa.memberbff.auth.service.BffOAuth2ClientService;
import com.springmsa.memberbff.presence.service.MemberPresenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
public class BffAuthController {

    private final BffOAuth2ClientService bffOAuth2ClientService;
    private final BffAuthenticationService bffAuthenticationService;
    private final MemberPresenceService memberPresenceService;

    @Value("${bff.oauth2.end-session-uri}")
    private String endSessionUri;

    @Value("${bff.frontend.redirect-uri}")
    private String frontendRedirectUri;

    @GetMapping("/auth/me")
    public ResponseEntity<MsaResponse<AuthMeResponse>> me(Authentication authentication, CsrfToken csrfToken) {
        // Touching the token makes CookieCsrfTokenRepository publish XSRF-TOKEN for the SPA.
        csrfToken.getToken();

        if (!bffAuthenticationService.isAuthenticated(authentication)) {
            return ResponseEntity.ok(MsaResponse.ok(AuthMeResponse.anonymous()));
        }

        return ResponseEntity.ok(MsaResponse.ok(AuthMeResponse.authenticated(bffAuthenticationService.getSessionUser(authentication))));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<MsaResponse<LogoutResponse>> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String idToken = bffOAuth2ClientService.getIdToken(authentication).orElse("");

        memberPresenceService.logout(request.getSession(false), authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        SecurityContextHolder.clearContext();

        String postLogoutRedirectUri = frontendRedirectUri + "/auth";

        if (!StringUtils.hasText(idToken)) {
            return ResponseEntity.ok(MsaResponse.ok(LogoutResponse.localSuccess()));
        }

        String authServerLogoutUrl = endSessionUrl(idToken, postLogoutRedirectUri);

        return ResponseEntity.ok(MsaResponse.ok(LogoutResponse.success(authServerLogoutUrl)));
    }

    private String endSessionUrl(String idToken, String postLogoutRedirectUri) {
        return UriComponentsBuilder.fromUriString(endSessionUri)
                .queryParam("id_token_hint", idToken)
                .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                .build()
                .encode()
                .toUriString();
    }
}
