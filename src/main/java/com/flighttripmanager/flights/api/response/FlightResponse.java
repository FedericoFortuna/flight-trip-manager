package com.flighttripmanager.flights.api.response;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
public record FlightResponse(UUID airlineId, String flightNumber, LocalDate flightDate, UUID originAirportId, UUID destinationAirportId, ScheduleResponse schedule, OperationResponse operation, BookingResponse booking, ConnectionProtectionValue connectionProtection, List<TravelerResponse> passengers) {}
