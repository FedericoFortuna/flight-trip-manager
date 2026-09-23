package com.flighttripmanager.catalog.domain.model;

import java.util.UUID;
import java.util.Objects;
import java.time.Instant;
import java.math.BigDecimal;
import java.time.ZoneId;

public record Airport(UUID id, String iataCode, String icaoCode, String name, String city, String country,
        BigDecimal latitude, BigDecimal longitude, String timezone, boolean active, Instant lastSyncedAt) {
    public Airport {
        Objects.requireNonNull(id);
        name = CatalogValues.text(name, 200);
        country = CatalogValues.code(country, "[A-Z]{2}");
        city = CatalogValues.text(city, 120);
        iataCode = CatalogValues.code(iataCode, "[A-Z]{3}");
        icaoCode = CatalogValues.optionalCode(icaoCode, "[A-Z]{4}");
        timezone = CatalogValues.text(timezone, 100);
        ZoneId.of(timezone);
        if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("Coordinates must be a pair");
        if (latitude != null && (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new IllegalArgumentException("Coordinates out of range");
        }
    }
}

