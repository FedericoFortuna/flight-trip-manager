package com.flighttripmanager.flightsearch.api.dto;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
public record SaveOptionRequest(
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED,minimum="0") Long version,
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED) UUID searchId,
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED,example="MOCK") String provider,
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String providerOfferId) {}
