package com.springmsa.memberstockservice.market.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class MarketDataCacheCodec {

    private final ObjectMapper objectMapper;

    <T> Optional<T> decode(String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JacksonException ignored) {
            return Optional.empty();
        }
    }

    Optional<String> encode(Object value) {
        try {
            return Optional.of(objectMapper.writeValueAsString(value));
        } catch (JacksonException ignored) {
            return Optional.empty();
        }
    }
}
