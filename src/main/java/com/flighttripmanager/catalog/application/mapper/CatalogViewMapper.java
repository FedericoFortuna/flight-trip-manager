package com.flighttripmanager.catalog.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import com.flighttripmanager.catalog.domain.model.*;
import com.flighttripmanager.catalog.application.contract.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogViewMapper {
    AirportView toView(Airport airport);
    AirlineView toView(Airline airline);
    LocationView toView(Location location);
    LocationType toDomain(LocationKind type);
}

