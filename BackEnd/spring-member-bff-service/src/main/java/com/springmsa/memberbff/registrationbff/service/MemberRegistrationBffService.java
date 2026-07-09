package com.springmsa.memberbff.registrationbff.service;

import com.springmsa.common.web.error.DownstreamErrorResponse;
import com.springmsa.common.web.error.MsaErrorResponseParser;
import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.registrationbff.client.UserServiceInternalClient;
import com.springmsa.memberbff.registrationbff.client.dto.UserCreateRequest;
import com.springmsa.memberbff.registrationbff.client.dto.UserCreateResponse;
import com.springmsa.memberbff.registrationbff.dto.RegistrationRequest;
import com.springmsa.memberbff.registrationbff.dto.RegistrationResponse;
import com.springmsa.memberbff.registrationbff.exception.MemberRegistrationException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberRegistrationBffService {

    private final UserServiceInternalClient userServiceInternalClient;

    public RegistrationResponse registerMember(RegistrationRequest request) {
        UserCreateRequest createRequest = UserCreateRequest.from(request, Set.of("ROLE_USER"));

        try {
            UserCreateResponse response = requireResponse(userServiceInternalClient.create(createRequest));
            return RegistrationResponse.from(response);

        } catch (FeignException e) {
            throw feignFailure(e);
        }
    }

    private <T> T requireResponse(MsaResponse<T> responseBody) {
        if (responseBody == null || responseBody.data() == null) {
            throw new MemberRegistrationException(HttpStatus.BAD_GATEWAY, "User service returned an empty registration response");
        }

        return responseBody.data();
    }

    private MemberRegistrationException feignFailure(FeignException e) {
        HttpStatusCode statusCode = resolveStatusCode(e);

        if (statusCode.is5xxServerError() && e.status() < 0) {
            return new MemberRegistrationException(statusCode, "User service is unavailable", e);
        }

        DownstreamErrorResponse downstreamError = resolveFeignError(e);

        return new MemberRegistrationException(
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
                MemberRegistrationException.CODE,
                "Member registration request failed"
        );
    }
}
