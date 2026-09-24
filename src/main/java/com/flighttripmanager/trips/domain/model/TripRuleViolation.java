package com.flighttripmanager.trips.domain.model;

public final class TripRuleViolation extends RuntimeException {
    private final String field;
    public TripRuleViolation(String field) { super("Invalid trip data"); this.field = field; }
    public String field() { return field; }
}
