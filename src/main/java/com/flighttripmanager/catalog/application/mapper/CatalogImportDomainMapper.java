package com.flighttripmanager.catalog.application.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.mapstruct.*;
import com.flighttripmanager.catalog.application.contract.*;
import com.flighttripmanager.catalog.domain.model.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogImportDomainMapper {
    @Mapping(target = "iataCode", qualifiedByName = "code")
    @Mapping(target = "icaoCode", qualifiedByName = "code")
    @Mapping(target = "country", qualifiedByName = "code")
    Airport airport(AirportImport input, UUID id, Instant lastSyncedAt);

    @Mapping(target = "iataCode", qualifiedByName = "code")
    @Mapping(target = "icaoCode", qualifiedByName = "code")
    @Mapping(target = "country", qualifiedByName = "code")
    Airline airline(AirlineImport input, UUID id, Instant lastSyncedAt);

    @Mapping(target = "country", qualifiedByName = "code")
    Location location(LocationImport input, UUID id);

    default String text(String value) { return value == null ? null : value.strip(); }

    @Named("code")
    default String code(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
    default BigDecimal coordinate(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.UNNECESSARY);
    }
}
