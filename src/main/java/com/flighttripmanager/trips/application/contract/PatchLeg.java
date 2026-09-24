package com.flighttripmanager.trips.application.contract;

import java.time.*;
public record PatchLeg(Long version, Change<PlaceRef> origin, Change<PlaceRef> destination,
        Change<TransportTypeValue> transportType, Change<LocalDate> departureDate, Change<Instant> departureDateTime,
        Change<LocalDate> arrivalDate, Change<Instant> arrivalDateTime, Change<LegStatusValue> status) {}
