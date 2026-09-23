package com.flighttripmanager.catalog.application.usecase;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.flighttripmanager.catalog.application.contract.*;
import com.flighttripmanager.catalog.application.port.in.CatalogLookup;
import com.flighttripmanager.catalog.application.port.out.CatalogStore;
import com.flighttripmanager.catalog.application.mapper.CatalogViewMapper;

@Service
@Transactional(readOnly = true)
public class CatalogQueryService implements CatalogLookup {
    private final CatalogStore store;
    private final CatalogViewMapper mapper;

    public CatalogQueryService(CatalogStore store, CatalogViewMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }
    @Override public CatalogPage<AirportView> airports(CatalogQuery query, boolean active) {
        return map(store.airports(query, active), mapper::toView);
    }
    @Override public AirportView airport(String iataCode) {
        return mapper.toView(store.airport(code(iataCode, "[A-Z]{3}"))
                .orElseThrow(() -> new CatalogNotFoundException("AIRPORT_NOT_FOUND")));
    }
    @Override public CatalogPage<AirlineView> airlines(CatalogQuery query, boolean active) {
        return map(store.airlines(query, active), mapper::toView);
    }
    @Override public AirlineView airline(String iataCode) {
        return mapper.toView(store.airline(code(iataCode, "[A-Z0-9]{2}"))
                .orElseThrow(() -> new CatalogNotFoundException("AIRLINE_NOT_FOUND")));
    }
    @Override public CatalogPage<LocationView> locations(CatalogQuery query, LocationKind type) {
        return map(store.locations(query, mapper.toDomain(type)), mapper::toView);
    }
    @Override public LocationView location(UUID id) {
        return mapper.toView(store.location(Objects.requireNonNull(id))
                .orElseThrow(() -> new CatalogNotFoundException("LOCATION_NOT_FOUND")));
    }
    private static String code(String raw, String pattern) {
        String normalized = Objects.requireNonNull(raw).toUpperCase(Locale.ROOT);
        if (!normalized.matches(pattern)) throw new IllegalArgumentException("Invalid IATA code");
        return normalized;
    }
    private static <T, R> CatalogPage<R> map(CatalogPage<T> page, Function<T, R> mapper) {
        return new CatalogPage<>(page.items().stream().map(mapper).toList(), page.page(), page.size(), page.totalElements());
    }
}

