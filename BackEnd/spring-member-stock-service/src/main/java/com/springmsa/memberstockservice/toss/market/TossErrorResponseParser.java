package com.springmsa.memberstockservice.toss.market;

import com.springmsa.memberstockservice.toss.market.dto.TossErrorEnvelope;
import com.springmsa.memberstockservice.toss.market.dto.TossErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class TossErrorResponseParser {

    private static final TossErrorResponse EMPTY_RESPONSE = new TossErrorResponse(null, null);

    private final ObjectMapper objectMapper;

    TossErrorResponse parse(String body) {
        if (body == null || body.isBlank()) {
            return EMPTY_RESPONSE;
        }

        try {
            TossErrorEnvelope envelope = objectMapper.readValue(body, TossErrorEnvelope.class);
            if (envelope.error() != null) {
                return envelope.error();
            }

            return objectMapper.readValue(body, TossErrorResponse.class);
        } catch (RuntimeException ignored) {
            return EMPTY_RESPONSE;
        }
    }
}
