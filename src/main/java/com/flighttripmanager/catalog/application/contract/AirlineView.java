package com.flighttripmanager.catalog.application.contract;

import java.util.UUID;
import java.time.Instant;

public record AirlineView(UUID id, String iataCode, String icaoCode, String name, String country, boolean active, Instant lastSyncedAt) {}

