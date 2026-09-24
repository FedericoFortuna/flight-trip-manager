package com.flighttripmanager.trips.api.response;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import com.flighttripmanager.trips.application.contract.*;
public record LegResponse(UUID id, UUID tripId, PlaceRef origin, PlaceRef destination, TransportTypeValue transportType,
        LocalDate departureDate, Instant departureDateTime, LocalDate arrivalDate, Instant arrivalDateTime,
        LegStatusValue status, LegStatusValue declaredStatus, Integer manualOrder,
        long tripVersion, Instant createdAt, Instant updatedAt) {}
