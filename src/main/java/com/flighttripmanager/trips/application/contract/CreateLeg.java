package com.flighttripmanager.trips.application.contract;

import java.time.*;
public record CreateLeg(Long version, PlaceRef origin, PlaceRef destination, TransportTypeValue transportType, LocalDate departureDate,
        Instant departureDateTime, LocalDate arrivalDate, Instant arrivalDateTime, LegStatusValue status) {}
