package com.springmsa.adminbff.user;

import com.springmsa.adminbff.auth.AdminBffOAuth2ClientService;
import com.springmsa.adminbff.client.AdminUserApiClient;
import com.springmsa.adminbff.user.dto.AdminCurrentUserResponse;
import com.springmsa.adminbff.user.dto.AdminUserResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminBffUserService {

    private final AdminUserApiClient adminUserApiClient;
    private final AdminBffOAuth2ClientService adminBffOAuth2ClientService;

    public AdminCurrentUserResponse getCurrentUser(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        try {
            return requireResponse(
                    adminUserApiClient.me(bearerToken(authentication, request, response)),
                    "User service returned an empty current user response"
            );

        } catch (ResponseStatusException e) {
            throw invalidSession(e);

        } catch (FeignException e) {
            throw feignFailure(e, "User service request failed");
        }
    }

    public List<AdminUserResponse> getAdminUsers(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        try {
            return requireResponse(
                    adminUserApiClient.users(bearerToken(authentication, request, response)),
                    "User service returned an empty admin users response"
            );

        } catch (ResponseStatusException e) {
            throw invalidSession(e);

        } catch (FeignException e) {
            throw feignFailure(e, "User admin users request failed");
        }
    }

    public AdminUserResponse getAdminUser(Long userId, Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        try {
            return requireResponse(
                    adminUserApiClient.user(bearerToken(authentication, request, response), userId),
                    "User service returned an empty admin user response"
            );

        } catch (ResponseStatusException e) {
            throw invalidSession(e);

        } catch (FeignException e) {
            throw feignFailure(e, "User admin user request failed");
        }
    }

    private String bearerToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return "Bearer " + adminBffOAuth2ClientService.getAccessToken(authentication, request, response);
    }

    private <T> T requireResponse(T responseBody, String emptyResponseMessage) {
        if (responseBody == null) {
            throw new AdminBffUserException(HttpStatus.BAD_GATEWAY, emptyResponseMessage);
        }

        return responseBody;
    }

    private AdminBffUserException invalidSession(ResponseStatusException e) {
        return new AdminBffUserException(
                e.getStatusCode(),
                resolveMessage(e, "Admin BFF session is invalid"),
                e
        );
    }

    private AdminBffUserException feignFailure(FeignException e, String fallbackMessage) {
        HttpStatusCode statusCode = resolveStatusCode(e);

        if (statusCode.is5xxServerError() && e.status() < 0) {
            return new AdminBffUserException(statusCode, "User service is unavailable", e);
        }

        return new AdminBffUserException(
                statusCode,
                resolveFeignErrorMessage(e, fallbackMessage),
                e
        );
    }

    private HttpStatusCode resolveStatusCode(FeignException e) {
        if (e.status() >= 100 && e.status() <= 599) {
            return HttpStatusCode.valueOf(e.status());
        }

        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    private String resolveFeignErrorMessage(FeignException e, String fallbackMessage) {
        String responseBody = e.contentUTF8();

        if (responseBody != null && !responseBody.isBlank()) {
            return responseBody;
        }

        return fallbackMessage;
    }

    private String resolveMessage(ResponseStatusException e, String fallbackMessage) {
        String reason = e.getReason();

        if (reason == null || reason.isBlank()) {
            return fallbackMessage;
        }

        return reason;
    }
}
