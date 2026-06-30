package com.springmsa.authserver.otp.common;

import com.springmsa.authserver.client.dto.AuthUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Service;

@Service
public class OtpLoginSessionService {

    private final OtpAuthenticationFactory otpAuthenticationFactory;
    private final SecurityContextRepository securityContextRepository;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public OtpLoginSessionService(
            OtpAuthenticationFactory otpAuthenticationFactory,
            SecurityContextRepository securityContextRepository
    ) {
        this.otpAuthenticationFactory = otpAuthenticationFactory;
        this.securityContextRepository = securityContextRepository;
    }

    public String login(
            AuthUserResponse user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return login(user, request, response, FactorGrantedAuthority.OTT_AUTHORITY);
    }

    public String loginWithPassword(
            AuthUserResponse user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return login(user, request, response, FactorGrantedAuthority.PASSWORD_AUTHORITY);
    }

    private String login(
            AuthUserResponse user,
            HttpServletRequest request,
            HttpServletResponse response,
            String factorAuthority
    ) {
        Authentication authentication =
                otpAuthenticationFactory.createAuthentication(user, request, factorAuthority);

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
