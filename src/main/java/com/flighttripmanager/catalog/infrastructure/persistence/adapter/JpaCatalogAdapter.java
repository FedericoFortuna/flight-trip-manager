package com.flighttripmanager.catalog.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.flighttripmanager.catalog.domain.model.*;
import com.flighttripmanager.catalog.application.contract.CatalogPage;
import com.flighttripmanager.catalog.application.contract.CatalogQuery;
import com.flighttripmanager.catalog.application.port.out.CatalogStore;
import com.flighttripmanager.catalog.infrastructure.persistence.repository.*;
import com.flighttripmanager.catalog.infrastructure.persistence.mapper.CatalogPersistenceMapper;

@Component
public class JpaCatalogAdapter implements CatalogStore {
    private final AirportJpaRepository airports;
    private final AirlineJpaRepository airlines;
    private final LocationJpaRepository locations;
    private final CatalogPersistenceMapper mapper;

    public JpaCatalogAdapter(AirportJpaRepository airports, AirlineJpaRepository airlines,
            LocationJpaRepository locations, CatalogPersistenceMapper mapper) {
        this.airports = airports;
        this.airlines = airlines;
        this.locations = locations;
        this.mapper = mapper;
    }
    @Override public CatalogPage<Airport> airports(CatalogQuery query, boolean active) {
        return map(airports.search(query.q(), active, pageable(query)), mapper::toDomain);
    }
    @Override public Optional<Airport> airport(String iataCode) {
        return airports.findByIataCode(iataCode).map(mapper::toDomain);
    }
    @Override public Optional<Airport> airportById(UUID id) {
        return airports.findById(id).map(mapper::toDomain);
    }
    @Override public CatalogPage<Airline> airlines(CatalogQuery query, boolean active) {
        return map(airlines.search(query.q(), active, pageable(query)), mapper::toDomain);
    }
    @Override public Optional<Airline> airline(String iataCode) {
        return airlines.findByIataCode(iataCode).map(mapper::toDomain);
    }
    @Override public CatalogPage<Location> locations(CatalogQuery query, LocationType type) {
        return map(locations.search(query.q(), type, pageable(query)), mapper::toDomain);
    }
    @Override public Optional<Location> location(UUID id) {
        return locations.findById(id).map(mapper::toDomain);
    }
    private static PageRequest pageable(CatalogQuery query) {
        return PageRequest.of(query.page(), query.size(), Sort.by("name", "id"));
    }
    private static <T, R> CatalogPage<R> map(Page<T> page, Function<T, R> mapper) {
        return new CatalogPage<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
