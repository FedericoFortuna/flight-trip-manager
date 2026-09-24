package com.flighttripmanager.catalog.application.port.in;

import java.util.UUID;
import com.flighttripmanager.catalog.application.contract.*;

public interface CatalogLookup {
    AirportView airportById(UUID id);
    CatalogPage<AirportView> airports(CatalogQuery query, boolean active);
    AirportView airport(String iataCode);
    CatalogPage<AirlineView> airlines(CatalogQuery query, boolean active);
    AirlineView airline(String iataCode);
    CatalogPage<LocationView> locations(CatalogQuery query, LocationKind type);
    LocationView location(UUID id);
}

