package com.flighttripmanager.flights.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record FlightView(UUID id, UUID tripId, UUID tripLegId, long tripVersion, FlightData data,
    Instant originalScheduledDeparture, Instant originalScheduledArrival, String provider, Instant lastSyncedAt,
    Instant createdAt, Instant updatedAt) {}
