package com.flighttripmanager.flights.domain.model;
public final class FlightRuleViolation extends RuntimeException {
    private final String field;
    public FlightRuleViolation(String field) { super("Invalid flight field"); this.field = field; }
    public String field() { return field; }
}
