package com.flighttripmanager.trips.application.contract;

public final class TripsException extends RuntimeException {
    public enum Reason { INVALID_REQUEST, INVALID_REFERENCE, TRIP_NOT_FOUND, LEG_NOT_FOUND, VERSION_CONFLICT, TRIP_NOT_EMPTY, CONFLICT }
    private final Reason reason;
    private final String field;
    public TripsException(Reason reason, String field) { super(reason.name()); this.reason = reason; this.field = field; }
    public Reason reason() { return reason; }
    public String field() { return field; }
}
