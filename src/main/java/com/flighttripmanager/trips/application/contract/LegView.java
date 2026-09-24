package com.flighttripmanager.trips.application.contract;

import java.time.*;
import java.util.UUID;
public record LegView(UUID id, UUID tripId, PlaceRef origin, PlaceRef destination, TransportTypeValue transportType,
        LocalDate departureDate, Instant departureDateTime, LocalDate arrivalDate, Instant arrivalDateTime,
        LegStatusValue status, LegStatusValue declaredStatus, Integer manualOrder,
        long tripVersion, Instant createdAt, Instant updatedAt) {}
