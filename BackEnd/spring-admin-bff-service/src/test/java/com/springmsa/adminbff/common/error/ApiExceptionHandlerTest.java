package com.springmsa.adminbff.common.error;

import com.springmsa.common.web.response.MsaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiExceptionHandlerTest {

    @Test
    void returnsNotFoundForUnknownResource() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<MsaResponse<Void>> response = handler.handleNoResourceFoundException(
                mock(NoResourceFoundException.class)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Resource not found");
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getBody().errors()).isEmpty();
    }
}
