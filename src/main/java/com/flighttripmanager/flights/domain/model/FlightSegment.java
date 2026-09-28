package com.flighttripmanager.flights.domain.model;
import java.time.*;
import java.util.*;
public record FlightSegment(UUID id, UUID tripId, UUID tripLegId, UUID airlineId, String flightNumber,
        LocalDate flightDate, UUID originAirportId, UUID destinationAirportId, FlightSchedule schedule,
        FlightOperation operation, FlightBooking booking, ConnectionProtection connectionProtection,
        List<FlightPassenger> passengers, Instant originalScheduledDeparture, Instant originalScheduledArrival,
        Instant createdAt, Instant updatedAt) {
    public FlightSegment {
        if (id == null || tripId == null || tripLegId == null || airlineId == null
                || originAirportId == null || destinationAirportId == null) throw new FlightRuleViolation("references");
        if (originAirportId.equals(destinationAirportId)) throw new FlightRuleViolation("destinationAirportId");
        flightNumber = FlightValues.code(flightNumber, 10, "flightNumber");
        if (flightNumber == null || !flightNumber.matches("[A-Z0-9]{2,3}[0-9]{1,4}[A-Z]?")) throw new FlightRuleViolation("flightNumber");
        if (flightDate == null || flightDate.getYear() < 1 || flightDate.getYear() > 9999) throw new FlightRuleViolation("flightDate");
        if (schedule == null) schedule = new FlightSchedule(null, null, null, null, null, null);
        if (operation == null) operation = new FlightOperation(null, null, null, null, null);
        if (booking == null) booking = new FlightBooking(null, null, null, null, null, null);
        if (connectionProtection == null) connectionProtection = ConnectionProtection.UNKNOWN;
        if (passengers == null) passengers = List.of();
        if (passengers.size() > 100 || passengers.stream().anyMatch(Objects::isNull)
                || passengers.stream().map(FlightPassenger::passengerId).distinct().count() != passengers.size()) {
            throw new FlightRuleViolation("passengers");
        }
        passengers = passengers.stream().sorted(Comparator.comparing(FlightPassenger::passengerId)).toList();
        FlightValues.pair(originalScheduledDeparture, originalScheduledArrival);
        if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) throw new FlightRuleViolation("timestamps");
    }
}
