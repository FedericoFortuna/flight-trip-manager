package com.flighttripmanager.flights.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record FlightData(UUID airlineId, String flightNumber, LocalDate flightDate, UUID originAirportId, UUID destinationAirportId, ScheduleData schedule, OperationData operation, BookingData booking, ConnectionProtectionValue connectionProtection, List<TravelerData> passengers) {}
