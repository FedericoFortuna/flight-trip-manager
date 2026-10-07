package com.flighttripmanager.flightsearch.api.dto;
import java.util.List;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;
public record CriteriaDto(
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED,example="EZE",pattern="[A-Za-z]{3}") String origin,
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED,example="MAD",pattern="[A-Za-z]{3}") String destination,
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="Local departure date; today through 365 days ahead") LocalDate departureDate,
    @Schema(description="Omit for one-way; must not precede departure") LocalDate returnDate,
    @Schema(minimum="0",maximum="3",defaultValue="0") int flexDays,
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED,minimum="1",maximum="9") int adults,
    @Schema(description="Ages 0-17; at most nine passengers total, at most one infant under two per adult") List<Integer> childAges,
    @Schema(allowableValues={"ECONOMY","PREMIUM_ECONOMY","BUSINESS","FIRST"},defaultValue="ECONOMY") String cabin,
    FiltersDto filters) {}
