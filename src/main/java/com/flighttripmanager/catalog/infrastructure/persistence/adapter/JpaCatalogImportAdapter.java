package com.flighttripmanager.catalog.infrastructure.persistence.adapter;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import com.flighttripmanager.catalog.application.contract.CatalogImportException;
import com.flighttripmanager.catalog.application.port.out.*;
import com.flighttripmanager.catalog.domain.model.*;
import com.flighttripmanager.catalog.infrastructure.persistence.repository.*;
import com.flighttripmanager.catalog.infrastructure.persistence.mapper.*;
import static com.flighttripmanager.catalog.application.contract.CatalogImportException.Reason.*;

@Component
public class JpaCatalogImportAdapter implements CatalogImportStore {
    private final AirportJpaRepository airports;
    private final AirlineJpaRepository airlines;
    private final LocationJpaRepository locations;
    private final CatalogPersistenceMapper readMapper;
    private final CatalogImportPersistenceMapper writeMapper;
    private final JdbcTemplate jdbc;

    public JpaCatalogImportAdapter(AirportJpaRepository airports, AirlineJpaRepository airlines,
            LocationJpaRepository locations, CatalogPersistenceMapper readMapper,
            CatalogImportPersistenceMapper writeMapper, JdbcTemplate jdbc) {
        this.airports = airports;
        this.airlines = airlines;
        this.locations = locations;
        this.readMapper = readMapper;
        this.writeMapper = writeMapper;
        this.jdbc = jdbc;
    }
    @Override public void acquireImportLock() {
        // Stable namespace/key, released automatically at commit or rollback. No waiting on busy imports.
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(734021, 1)", Boolean.class))) {
            throw new CatalogImportException(IMPORT_BUSY, "request");
        }
    }
    @Override public Optional<StoredCatalogEntry<Airport>> airport(String source, String externalId) {
        return airports.findBySourceAndExternalId(source, externalId)
                .map(entity -> new StoredCatalogEntry<>(readMapper.toDomain(entity), entity.getSourceObservedAt()));
    }
    @Override public Optional<StoredCatalogEntry<Airline>> airline(String source, String externalId) {
        return airlines.findBySourceAndExternalId(source, externalId)
                .map(entity -> new StoredCatalogEntry<>(readMapper.toDomain(entity), entity.getSourceObservedAt()));
    }
    @Override public Optional<StoredCatalogEntry<Location>> location(String source, String externalId) {
        return locations.findBySourceAndExternalId(source, externalId)
                .map(entity -> new StoredCatalogEntry<>(readMapper.toDomain(entity), entity.getSourceObservedAt()));
    }
    @Override public void save(Airport value, ImportMetadata metadata) {
        persist(() -> airports.saveAndFlush(writeMapper.toEntity(value, metadata)));
    }
    @Override public void save(Airline value, ImportMetadata metadata) {
        persist(() -> airlines.saveAndFlush(writeMapper.toEntity(value, metadata)));
    }
    @Override public void save(Location value, ImportMetadata metadata) {
        persist(() -> locations.saveAndFlush(writeMapper.toEntity(value, metadata)));
    }
    private static void persist(Runnable write) {
        try {
            write.run();
        } catch (DataIntegrityViolationException exception) {
            // Leave the failed transaction; never continue importing a partially applied batch.
            throw new CatalogImportException(IDENTITY_CONFLICT, "items");
        }
    }
}
