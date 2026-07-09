package com.springmsa.common.web.error;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MsaErrorResponseParserTest {

    @Test
    void parsesMsaErrorEnvelope() {
        String body = """
                {
                  "success": false,
                  "code": "VALIDATION_FAILED",
                  "message": "Validation failed",
                  "status": 400,
                  "data": null,
                  "errors": [
                    {
                      "field": "loginId",
                      "message": "must not be blank"
                    }
                  ]
                }
                """;

        Optional<DownstreamErrorResponse> response = MsaErrorResponseParser.parse(body);

        assertTrue(response.isPresent());
        assertEquals("VALIDATION_FAILED", response.get().code());
        assertEquals("Validation failed", response.get().message());
        assertEquals(1, response.get().errors().size());
        assertEquals("loginId", response.get().errors().get(0).field());
        assertEquals("must not be blank", response.get().errors().get(0).message());
    }

    @Test
    void returnsDefaultForNonJsonBody() {
        DownstreamErrorResponse response = MsaErrorResponseParser.parseOrDefault(
                "<html>error</html>",
                "FALLBACK_CODE",
                "Fallback message"
        );

        assertEquals("FALLBACK_CODE", response.code());
        assertEquals("Fallback message", response.message());
        assertTrue(response.errors().isEmpty());
    }
}
