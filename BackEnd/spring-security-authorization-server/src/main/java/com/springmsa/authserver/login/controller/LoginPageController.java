package com.springmsa.authserver.login.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

@Controller
public class LoginPageController {

    private static final String ADMIN_CLIENT_ID = "admin-bff-client";

    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Value("${app.frontend.user-login-uri}")
    private String userLoginUri;

    @Value("${app.frontend.admin-login-uri}")
    private String adminLoginUri;

    @GetMapping("/login")
    public RedirectView loginPage(HttpServletRequest request, HttpServletResponse response) {
        String targetLoginUri = resolveTargetLoginUri(request, response);
        String redirectUri = UriComponentsBuilder.fromUriString(targetLoginUri)
                .queryParamIfPresent("logout", optionalQueryValue(request, "logout"))
                .queryParamIfPresent("error", optionalQueryValue(request, "error"))
                .build()
                .encode()
                .toUriString();

        return new RedirectView(redirectUri);
    }

    private String resolveTargetLoginUri(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);

        if (savedRequest != null && savedRequest.getRedirectUrl().contains("client_id=" + ADMIN_CLIENT_ID)) {
            return adminLoginUri;
        }

        return userLoginUri;
    }

    private java.util.Optional<String> optionalQueryValue(HttpServletRequest request, String name) {
        String value = request.getParameter(name);

        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(value);
    }
}
