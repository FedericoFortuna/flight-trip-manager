package com.flighttripmanager.flights.api.response;
import java.util.UUID;
import java.time.Instant;
public record FlightViewResponse(UUID id, UUID tripId, UUID tripLegId, long tripVersion, FlightResponse data,
    Instant originalScheduledDeparture, Instant originalScheduledArrival, String provider, Instant lastSyncedAt,
    Instant createdAt, Instant updatedAt) {}
