package com.springmsa.memberbff.registration;

import com.springmsa.memberbff.client.UserInternalClient;
import com.springmsa.memberbff.client.dto.UserCreateRequest;
import com.springmsa.memberbff.client.dto.UserCreateResponse;
import com.springmsa.memberbff.registration.dto.RegistrationRequest;
import com.springmsa.memberbff.registration.dto.RegistrationResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserInternalClient userInternalClient;

    public RegistrationResponse registerMember(RegistrationRequest request) {
        UserCreateRequest createRequest = UserCreateRequest.from(request, Set.of("ROLE_USER"));

        try {
            UserCreateResponse response = requireResponse(userInternalClient.create(createRequest));
            return RegistrationResponse.from(response);

        } catch (FeignException e) {
            throw feignFailure(e);
        }
    }

    private <T> T requireResponse(T responseBody) {
        if (responseBody == null) {
            throw new RegistrationException(HttpStatus.BAD_GATEWAY, "User service returned an empty registration response");
        }

        return responseBody;
    }

    private RegistrationException feignFailure(FeignException e) {
        HttpStatusCode statusCode = resolveStatusCode(e);

        if (statusCode.is5xxServerError() && e.status() < 0) {
            return new RegistrationException(statusCode, "User service is unavailable", e);
        }

        return new RegistrationException(statusCode, resolveFeignErrorMessage(e), e);
    }

    private HttpStatusCode resolveStatusCode(FeignException e) {
        if (e.status() >= 100 && e.status() <= 599) {
            return HttpStatusCode.valueOf(e.status());
        }

        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    private String resolveFeignErrorMessage(FeignException e) {
        String responseBody = e.contentUTF8();

        if (responseBody != null && !responseBody.isBlank()) {
            return responseBody;
        }

        return "Member registration request failed";
    }
}
