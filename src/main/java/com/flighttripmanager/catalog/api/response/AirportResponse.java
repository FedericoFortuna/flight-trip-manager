package com.flighttripmanager.catalog.api.response;

import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;

public record AirportResponse(UUID id, String iataCode, String icaoCode, String name, String city, String country,
        BigDecimal latitude, BigDecimal longitude, String timezone, boolean active, Instant lastSyncedAt) {}

