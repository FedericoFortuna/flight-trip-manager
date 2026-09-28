package com.flighttripmanager.flights.application.contract;
public record FlightQuery(int page, int size) {
    public FlightQuery {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) throw new FlightException(FlightException.Reason.INVALID_REQUEST, "pagination");
    }
}
