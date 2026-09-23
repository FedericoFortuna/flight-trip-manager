package com.flighttripmanager.shared.api.error;

import java.time.Clock;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ApiErrorFactory {
    private final Clock clock;

    public ApiErrorFactory(Clock clock) {
        this.clock = clock;
    }

    public ApiError forStatus(int status) {
        return switch (status) {
            case 400 -> create("INVALID_REQUEST", "Invalid request", Map.of());
            case 404 -> create("RESOURCE_NOT_FOUND", "Resource not found", Map.of());
            case 405 -> create("METHOD_NOT_ALLOWED", "HTTP method not allowed", Map.of());
            case 406 -> create("NOT_ACCEPTABLE", "Requested response format is not supported", Map.of());
            case 409 -> create("CONFLICT", "Request conflicts with current state", Map.of());
            case 413 -> create("PAYLOAD_TOO_LARGE", "Request payload is too large", Map.of());
            case 415 -> create("UNSUPPORTED_MEDIA_TYPE", "Request media type is not supported", Map.of());
            case 429 -> create("RATE_LIMIT_EXCEEDED", "Too many requests", Map.of());
            case 503 -> create("SERVICE_UNAVAILABLE", "Service temporarily unavailable", Map.of());
            default -> status >= 500
                    ? create("INTERNAL_ERROR", "An unexpected error occurred", Map.of())
                    : create("REQUEST_REJECTED", "Request could not be processed", Map.of());
        };
    }

    public ApiError validation(Map<String, String> fields) {
        return create("VALIDATION_ERROR", "Request validation failed", fields);
    }

    private ApiError create(String code, String message, Map<String, String> details) {
        return new ApiError(code, message, details, clock.instant());
    }
}
