package com.flighttripmanager.trips.api.request;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import com.flighttripmanager.trips.application.contract.*;
public record CreateLegRequest(Long version, PlaceRef origin, PlaceRef destination, TransportTypeValue transportType,
        LocalDate departureDate, Instant departureDateTime, LocalDate arrivalDate, Instant arrivalDateTime, LegStatusValue status) {}
