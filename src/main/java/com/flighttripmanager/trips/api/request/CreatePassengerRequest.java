package com.flighttripmanager.trips.api.request;
import io.swagger.v3.oas.annotations.media.Schema;
public record CreatePassengerRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", description = "Current trip version") Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100) String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100) String lastName,
        @Schema(nullable = true, maxLength = 2000, description = "Optional notes; do not include identity documents") String notes) {}
