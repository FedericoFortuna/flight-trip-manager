package com.flighttripmanager.flights.application.contract;
public final class FlightException extends RuntimeException {
    public enum Reason { INVALID_REQUEST, INVALID_REFERENCE, NOT_FOUND, VERSION_CONFLICT, CONFLICT }
    private final Reason reason;
    private final String field;
    public FlightException(Reason reason, String field) { super(reason.name()); this.reason = reason; this.field = field; }
    public Reason reason() { return reason; }
    public String field() { return field; }
}
