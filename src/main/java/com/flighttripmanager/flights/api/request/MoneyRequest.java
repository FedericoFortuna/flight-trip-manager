package com.flighttripmanager.flights.api.request;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
import io.swagger.v3.oas.annotations.media.Schema;

public record MoneyRequest(
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED, minimum="0", description="Known amount, at most two decimal places; never silently rounded") BigDecimal amount,
    @Schema(requiredMode=Schema.RequiredMode.REQUIRED, example="USD") String currency,
    @Schema(nullable=true, description="Known USD conversion only; automatically equals amount for USD") BigDecimal amountUsd) {}
