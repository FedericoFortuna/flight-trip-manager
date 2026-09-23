package com.flighttripmanager.catalog.domain.model;

import java.util.UUID;
import java.util.Objects;

public record Location(UUID id, LocationType type, String name, String city, String country) {
    public Location {
        Objects.requireNonNull(id);
        name = CatalogValues.text(name, 200);
        country = CatalogValues.code(country, "[A-Z]{2}");
        city = CatalogValues.text(city, 120);
        Objects.requireNonNull(type);
    }
}

