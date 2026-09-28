package com.flighttripmanager.flights.application.port.out;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;

public interface FlightTrips {
    record Context(long version, Instant updatedAt) {}
    Context inspect(UUID tripId, UUID legId);
    Context lock(UUID tripId, UUID legId, List<UUID> passengers);
    long advance(UUID tripId, long version);
}
