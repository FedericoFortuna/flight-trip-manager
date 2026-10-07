package com.flighttripmanager.trips.application.contract;
import java.time.Instant;
import java.util.UUID;
public record LegAlternativesClosed(UUID tripId, UUID legId, String reason, Instant at) {}
