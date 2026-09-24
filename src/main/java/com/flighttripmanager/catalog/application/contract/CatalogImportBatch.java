package com.flighttripmanager.catalog.application.contract;

import java.time.Instant;
import java.util.List;

/** Complete values for each supplied record; absent records remain untouched. */
public record CatalogImportBatch(String source, Instant observedAt, List<AirportImport> airports,
        List<AirlineImport> airlines, List<LocationImport> locations) {}
