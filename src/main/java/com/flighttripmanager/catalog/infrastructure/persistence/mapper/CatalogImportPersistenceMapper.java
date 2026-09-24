package com.flighttripmanager.catalog.infrastructure.persistence.mapper;

import org.mapstruct.*;
import com.flighttripmanager.catalog.domain.model.*;
import com.flighttripmanager.catalog.application.port.out.ImportMetadata;
import com.flighttripmanager.catalog.infrastructure.persistence.entity.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogImportPersistenceMapper {
    @Mapping(target = "sourceObservedAt", source = "metadata.observedAt")
    @Mapping(target = "lastSyncedAt", source = "metadata.syncedAt")
    AirportJpaEntity toEntity(Airport value, ImportMetadata metadata);

    @Mapping(target = "sourceObservedAt", source = "metadata.observedAt")
    @Mapping(target = "lastSyncedAt", source = "metadata.syncedAt")
    AirlineJpaEntity toEntity(Airline value, ImportMetadata metadata);

    @Mapping(target = "sourceObservedAt", source = "metadata.observedAt")
    @Mapping(target = "lastSyncedAt", source = "metadata.syncedAt")
    LocationJpaEntity toEntity(Location value, ImportMetadata metadata);
}
