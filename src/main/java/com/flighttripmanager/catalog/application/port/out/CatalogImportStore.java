package com.flighttripmanager.catalog.application.port.out;

import java.util.Optional;
import com.flighttripmanager.catalog.domain.model.*;

public interface CatalogImportStore {
    /** Nonblocking transaction-scoped lock shared by all catalog imports. */
    void acquireImportLock();
    Optional<StoredCatalogEntry<Airport>> airport(String source, String externalId);
    Optional<StoredCatalogEntry<Airline>> airline(String source, String externalId);
    Optional<StoredCatalogEntry<Location>> location(String source, String externalId);
    void save(Airport airport, ImportMetadata metadata);
    void save(Airline airline, ImportMetadata metadata);
    void save(Location location, ImportMetadata metadata);
}
