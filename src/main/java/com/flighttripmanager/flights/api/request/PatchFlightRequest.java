package com.flighttripmanager.flights.api.request;
import java.util.*; import java.time.*;
import com.flighttripmanager.flights.application.contract.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record PatchFlightRequest(@Schema(requiredMode=Schema.RequiredMode.REQUIRED, minimum="0") Long version,
    UUID airlineId, String flightNumber, LocalDate flightDate, UUID originAirportId, UUID destinationAirportId, ScheduleRequest schedule, OperationRequest operation, BookingRequest booking, ConnectionProtectionValue connectionProtection, List<TravelerRequest> passengers) {}
