package com.springmsa.common.web.error;

import com.springmsa.common.web.response.FieldErrorResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MsaErrorResponseParser {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private MsaErrorResponseParser() {
    }

    public static Optional<DownstreamErrorResponse> parse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);

            if (root == null || !root.isObject()) {
                return Optional.empty();
            }

            String code = text(root.get("code"));
            String message = text(root.get("message"));
            List<FieldErrorResponse> errors = errors(root.get("errors"));

            if (!hasText(code) && !hasText(message) && errors.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new DownstreamErrorResponse(code, message, errors));

        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    public static DownstreamErrorResponse parseOrDefault(
            String responseBody,
            String fallbackCode,
            String fallbackMessage
    ) {
        return parse(responseBody)
                .map(error -> error.withFallbacks(fallbackCode, fallbackMessage))
                .orElseGet(() -> new DownstreamErrorResponse(fallbackCode, fallbackMessage, List.of()));
    }

    private static List<FieldErrorResponse> errors(JsonNode errorsNode) {
        if (errorsNode == null || !errorsNode.isArray()) {
            return List.of();
        }

        List<FieldErrorResponse> errors = new ArrayList<>();
        for (JsonNode errorNode : errorsNode) {
            if (!errorNode.isObject()) {
                continue;
            }

            String field = text(errorNode.get("field"));
            String message = text(errorNode.get("message"));

            if (!hasText(field) && !hasText(message)) {
                continue;
            }

            errors.add(new FieldErrorResponse(field, message));
        }

        return List.copyOf(errors);
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        String value = node.asString();
        return hasText(value) ? value : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
