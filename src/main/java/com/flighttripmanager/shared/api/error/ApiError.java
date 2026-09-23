package com.flighttripmanager.shared.api.error;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error público. Nunca incluye stacktrace ni valores rechazados.")
public record ApiError(
        @Schema(example = "VALIDATION_ERROR") String code,
        @Schema(example = "Request validation failed") String message,
        @Schema(description = "Campos inválidos y mensajes seguros; vacío si no corresponde") Map<String, String> details,
        @Schema(description = "Instante UTC del error") Instant timestamp) {
    public ApiError {
        details = Map.copyOf(details);
    }
}
