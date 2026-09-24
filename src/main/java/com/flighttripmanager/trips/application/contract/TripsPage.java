package com.flighttripmanager.trips.application.contract;

import java.util.List;
public record TripsPage<T>(List<T> items, int page, int size, long totalElements) {
    public TripsPage { items = List.copyOf(items); }
    public long totalPages() { return totalElements / size + (totalElements % size == 0 ? 0 : 1); }
}
