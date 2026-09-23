package com.flighttripmanager.catalog.application.port.out;

import java.util.Optional;
import java.util.UUID;
import com.flighttripmanager.catalog.domain.model.*;
import com.flighttripmanager.catalog.application.contract.CatalogPage;
import com.flighttripmanager.catalog.application.contract.CatalogQuery;

public interface CatalogStore {
    CatalogPage<Airport> airports(CatalogQuery query, boolean active);
    Optional<Airport> airport(String iataCode);
    CatalogPage<Airline> airlines(CatalogQuery query, boolean active);
    Optional<Airline> airline(String iataCode);
    CatalogPage<Location> locations(CatalogQuery query, LocationType type);
    Optional<Location> location(UUID id);
}

