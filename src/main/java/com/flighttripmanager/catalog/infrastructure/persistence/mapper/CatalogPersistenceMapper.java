package com.flighttripmanager.catalog.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import com.flighttripmanager.catalog.domain.model.*;
import com.flighttripmanager.catalog.infrastructure.persistence.entity.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogPersistenceMapper {
    Airport toDomain(AirportJpaEntity entity);
    Airline toDomain(AirlineJpaEntity entity);
    Location toDomain(LocationJpaEntity entity);
}
