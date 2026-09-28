package com.flighttripmanager.flights.api.response;
import java.util.List;
public record FlightPageResponse<T>(List<T> items, int page, int size, long totalElements, long totalPages) {}
