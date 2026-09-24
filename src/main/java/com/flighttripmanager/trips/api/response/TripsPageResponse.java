package com.flighttripmanager.trips.api.response;

import java.util.List;
public record TripsPageResponse<T>(List<T> items, int page, int size, long totalElements, long totalPages) {
    public TripsPageResponse { items = List.copyOf(items); }
}
