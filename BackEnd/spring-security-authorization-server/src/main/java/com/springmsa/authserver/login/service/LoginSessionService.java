package com.springmsa.authserver.login.service;

import com.springmsa.authserver.user.dto.AuthUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginSessionService {

    private final LoginAuthenticationFactory loginAuthenticationFactory;
    private final SecurityContextRepository securityContextRepository;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public String loginWithPassword(AuthUserResponse user, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = loginAuthenticationFactory.createPasswordAuthentication(user, request);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        securityContextRepository.saveContext(securityContext, request, response);

        SavedRequest savedRequest = requestCache.getRequest(request, response);

        if (savedRequest == null) {
            return null;
        }

        return savedRequest.getRedirectUrl();
    }
}
