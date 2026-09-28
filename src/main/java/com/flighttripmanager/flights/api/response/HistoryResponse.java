package com.flighttripmanager.flights.api.response;
import java.util.UUID;
import java.time.Instant;
public record HistoryResponse(UUID id, UUID flightId, long revision, String field, String previousValue, String value, String source, Instant recordedAt) {}
