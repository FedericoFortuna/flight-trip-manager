package com.flighttripmanager.trips.application.contract;
import java.time.Instant;
public record OptionTripContext(long version, Instant updatedAt, PlaceRef origin, PlaceRef destination,
        boolean acceptingAlternatives) {}
