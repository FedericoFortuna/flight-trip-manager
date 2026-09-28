package com.flighttripmanager.flights.api.request;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
public record FlightRequest(
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode=io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) UUID airlineId,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode=io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED, example="AR1132") String flightNumber,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode=io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED, description="Departure date in origin airport timezone") LocalDate flightDate,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode=io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) UUID originAirportId,
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode=io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) UUID destinationAirportId,
    ScheduleRequest schedule, OperationRequest operation, BookingRequest booking,
    ConnectionProtectionValue connectionProtection,
    @io.swagger.v3.oas.annotations.media.ArraySchema(maxItems=100) List<TravelerRequest> passengers) {}
