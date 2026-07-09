package com.springmsa.memberbff.common.error;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.common.web.error.DownstreamErrorResponse;
import com.springmsa.common.web.error.MsaErrorResponseParser;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public final class DownstreamFeignExceptionMapper {

    private DownstreamFeignExceptionMapper() {
    }

    public static ApiException toApiException(
            FeignException exception,
            String fallbackCode,
            String fallbackMessage,
            String unavailableMessage
    ) {
        HttpStatusCode statusCode = resolveStatusCode(exception);

        if (statusCode.is5xxServerError() && exception.status() < 0) {
            return new ApiException(statusCode, fallbackCode, unavailableMessage, exception);
        }

        DownstreamErrorResponse downstreamError = MsaErrorResponseParser.parseOrDefault(
                exception.contentUTF8(),
                fallbackCode,
                fallbackMessage
        );

        return new ApiException(
                statusCode,
                downstreamError.code(),
                downstreamError.message(),
                downstreamError.errors(),
                exception
        );
    }

    private static HttpStatusCode resolveStatusCode(FeignException exception) {
        if (exception.status() >= 100 && exception.status() <= 599) {
            return HttpStatusCode.valueOf(exception.status());
        }

        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
