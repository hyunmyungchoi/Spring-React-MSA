package com.springmsa.adminbff.registration.service;

import com.springmsa.adminbff.registration.client.AdminUserInternalClient;
import com.springmsa.adminbff.registration.client.dto.AdminUserCreateRequest;
import com.springmsa.adminbff.registration.client.dto.AdminUserCreateResponse;
import com.springmsa.adminbff.registration.dto.AdminRegistrationRequest;
import com.springmsa.adminbff.registration.dto.AdminRegistrationResponse;
import com.springmsa.adminbff.registration.exception.AdminBffRegistrationException;
import com.springmsa.common.web.error.DownstreamErrorResponse;
import com.springmsa.common.web.error.MsaErrorResponseParser;
import com.springmsa.common.web.response.MsaResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminBffRegistrationService {

    private final AdminUserInternalClient adminUserInternalClient;

    public AdminRegistrationResponse registerAdmin(AdminRegistrationRequest request) {
        AdminUserCreateRequest createRequest = AdminUserCreateRequest.from(request, Set.of("ROLE_USER", "ROLE_ADMIN"));

        try {
            AdminUserCreateResponse response = requireResponse(
                    adminUserInternalClient.create(createRequest)
            );

            return AdminRegistrationResponse.from(response);

        } catch (FeignException e) {
            throw feignFailure(e);
        }
    }

    private <T> T requireResponse(MsaResponse<T> responseBody) {
        if (responseBody == null || responseBody.data() == null) {
            throw new AdminBffRegistrationException(HttpStatus.BAD_GATEWAY, "User service returned an empty registration response");
        }

        return responseBody.data();
    }

    private AdminBffRegistrationException feignFailure(FeignException e) {
        HttpStatusCode statusCode = resolveStatusCode(e);

        if (statusCode.is5xxServerError() && e.status() < 0) {
            return new AdminBffRegistrationException(statusCode, "User service is unavailable", e);
        }

        DownstreamErrorResponse downstreamError = resolveFeignError(e);

        return new AdminBffRegistrationException(
                statusCode,
                downstreamError.code(),
                downstreamError.message(),
                downstreamError.errors(),
                e
        );
    }

    private HttpStatusCode resolveStatusCode(FeignException e) {
        if (e.status() >= 100 && e.status() <= 599) {
            return HttpStatusCode.valueOf(e.status());
        }

        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    private DownstreamErrorResponse resolveFeignError(FeignException e) {
        return MsaErrorResponseParser.parseOrDefault(
                e.contentUTF8(),
                AdminBffRegistrationException.CODE,
                "Admin registration request failed"
        );
    }
}
