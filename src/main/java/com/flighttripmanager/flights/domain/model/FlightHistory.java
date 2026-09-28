package com.flighttripmanager.flights.domain.model;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record FlightHistory(UUID id, UUID flightId, long revision, String field, String previousValue, String value, String source, Instant recordedAt) {
    public static List<FlightHistory> changes(FlightSegment old, FlightSegment next, long revision) {
        List<FlightHistory> result = new ArrayList<>();
        Map<String,String> before = values(old), after = values(next);
        after.forEach((field, value) -> {
            if (!Objects.equals(before.get(field), value)) result.add(new FlightHistory(UUID.randomUUID(), next.id(), revision, field, before.get(field), value, "MANUAL", next.updatedAt()));
        });
        return List.copyOf(result);
    }
    private static Map<String,String> values(FlightSegment flight) {
        Map<String,String> result = new LinkedHashMap<>();
        if (flight == null) return result;
        result.put("flightNumber", flight.flightNumber());
        result.put("status", flight.operation().status().name());
        result.put("departureTerminal", flight.operation().departureTerminal());
        result.put("departureGate", flight.operation().departureGate());
        result.put("arrivalTerminal", flight.operation().arrivalTerminal());
        result.put("arrivalGate", flight.operation().arrivalGate());
        return result;
    }
}
