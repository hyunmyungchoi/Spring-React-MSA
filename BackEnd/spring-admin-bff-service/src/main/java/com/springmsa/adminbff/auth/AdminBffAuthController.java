package com.springmsa.adminbff.auth;

import com.springmsa.adminbff.auth.dto.AdminAuthMeResponse;
import com.springmsa.adminbff.auth.dto.AdminLogoutResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
public class AdminBffAuthController {

    private final AdminBffOAuth2ClientService adminBffOAuth2ClientService;
    private final AdminBffAuthenticationService adminBffAuthenticationService;

    @Value("${admin-bff.oauth2.end-session-uri}")
    private String endSessionUri;

    @Value("${admin-bff.oauth2.logout-uri}")
    private String logoutUri;

    @Value("${admin-bff.frontend.redirect-uri}")
    private String frontendRedirectUri;

    @GetMapping("/auth/me")
    public ResponseEntity<AdminAuthMeResponse> me(Authentication authentication, CsrfToken csrfToken) {
        // Touching the token makes CookieCsrfTokenRepository publish XSRF-TOKEN for the SPA.
        csrfToken.getToken();

        if (!adminBffAuthenticationService.isAuthenticated(authentication)) {
            return ResponseEntity.ok(AdminAuthMeResponse.anonymous());
        }

        if (!adminBffAuthenticationService.hasAdminRole(authentication)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(AdminAuthMeResponse.adminRoleRequired());
        }

        return ResponseEntity.ok(
                AdminAuthMeResponse.authenticated(
                        adminBffAuthenticationService.getSessionUser(authentication)
                )
        );
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<AdminLogoutResponse> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {

        String idToken = adminBffOAuth2ClientService.getIdToken(authentication)
                .orElse("");

        new SecurityContextLogoutHandler().logout(request, response, authentication);
        SecurityContextHolder.clearContext();

        String postLogoutRedirectUri = frontendRedirectUri + "/auth";

        String authServerLogoutUrl = StringUtils.hasText(idToken)
                ? UriComponentsBuilder.fromUriString(endSessionUri)
                        .queryParam("id_token_hint", idToken)
                        .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                        .build()
                        .encode()
                        .toUriString()

                : UriComponentsBuilder.fromUriString(logoutUri)
                        .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                        .build()
                        .encode()
                        .toUriString();

        return ResponseEntity.ok(AdminLogoutResponse.success(authServerLogoutUrl));
    }
}
