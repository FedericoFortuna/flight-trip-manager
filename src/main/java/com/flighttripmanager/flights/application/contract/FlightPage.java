package com.flighttripmanager.flights.application.contract;
import java.util.List;
public record FlightPage<T>(List<T> items, int page, int size, long totalElements) {
    public FlightPage { items = List.copyOf(items); }
    public long totalPages() { return totalElements / size + (totalElements % size == 0 ? 0 : 1); }
}
