package com.flighttripmanager.flights.api.request;
import io.swagger.v3.oas.annotations.media.Schema;
public record CreateFlightRequest(
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED, minimum="0", description="Current trip version") Long version,
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED) FlightRequest data) {}
