package com.flighttripmanager.trips.application.contract;

import java.util.Locale;
public record TripsQuery(String q, int page, int size) {
    public TripsQuery {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100 || (q != null && q.length() > 100)) {
            throw new TripsException(TripsException.Reason.INVALID_REQUEST, "pagination");
        }
        q = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
    }
}
