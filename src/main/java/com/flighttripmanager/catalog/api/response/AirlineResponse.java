package com.flighttripmanager.catalog.api.response;

import java.util.UUID;
import java.time.Instant;

public record AirlineResponse(UUID id, String iataCode, String icaoCode, String name, String country, boolean active, Instant lastSyncedAt) {}

