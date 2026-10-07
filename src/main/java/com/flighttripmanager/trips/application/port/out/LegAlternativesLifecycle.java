package com.flighttripmanager.trips.application.port.out;
import java.time.Instant;
import java.util.UUID;
/** Called in the trip mutation transaction, after acquiring its parent lock. */
public interface LegAlternativesLifecycle {
    void close(UUID tripId, UUID legId, String reason, Instant at);
}
