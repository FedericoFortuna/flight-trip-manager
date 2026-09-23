package com.flighttripmanager.catalog.domain.model;

import java.util.UUID;
import java.util.Objects;
import java.time.Instant;

public record Airline(UUID id, String iataCode, String icaoCode, String name, String country, boolean active, Instant lastSyncedAt) {
    public Airline {
        Objects.requireNonNull(id);
        name = CatalogValues.text(name, 200);
        country = CatalogValues.code(country, "[A-Z]{2}");
        iataCode = CatalogValues.code(iataCode, "[A-Z0-9]{2}");
        icaoCode = CatalogValues.optionalCode(icaoCode, "[A-Z]{3}");
    }
}

