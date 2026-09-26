package com.flighttripmanager.trips.api.request;
import io.swagger.v3.oas.annotations.media.Schema;
public record PatchPassengerRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", description = "Current trip version") Long version,
        @Schema(minLength = 1, maxLength = 100) String firstName,
        @Schema(minLength = 1, maxLength = 100) String lastName,
        @Schema(nullable = true, maxLength = 2000, description = "Omit to preserve; null clears notes") String notes) {}
