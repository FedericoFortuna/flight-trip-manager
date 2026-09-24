package com.flighttripmanager.trips.api.request;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import com.flighttripmanager.trips.application.contract.*;
/** OpenAPI schema for the flat merge-patch body. */
public record PatchLegRequest(Long version, PlaceRef origin, PlaceRef destination, TransportTypeValue transportType,
        LocalDate departureDate, Instant departureDateTime, LocalDate arrivalDate, Instant arrivalDateTime, LegStatusValue status) {}
