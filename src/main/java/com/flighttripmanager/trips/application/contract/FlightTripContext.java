package com.flighttripmanager.trips.application.contract;
import java.time.Instant;
import java.util.UUID;
public record FlightTripContext(UUID tripId, UUID legId, long version, Instant updatedAt) {}
