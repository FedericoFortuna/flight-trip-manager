package com.flighttripmanager.trips.domain.model;

import java.util.UUID;
public record Place(PlaceType kind, UUID id) {
    public Place { if (kind == null || id == null) throw new TripRuleViolation("place"); }
}
