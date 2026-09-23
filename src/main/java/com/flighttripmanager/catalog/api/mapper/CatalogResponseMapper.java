package com.flighttripmanager.catalog.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import com.flighttripmanager.catalog.application.contract.*;
import com.flighttripmanager.catalog.api.response.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogResponseMapper {
    AirportResponse toResponse(AirportView view);
    AirlineResponse toResponse(AirlineView view);
    LocationResponse toResponse(LocationView view);

    default CatalogPageResponse<AirportResponse> airports(CatalogPage<AirportView> page) {
        return new CatalogPageResponse<>(page.items().stream().map(this::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
    default CatalogPageResponse<AirlineResponse> airlines(CatalogPage<AirlineView> page) {
        return new CatalogPageResponse<>(page.items().stream().map(this::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
    default CatalogPageResponse<LocationResponse> locations(CatalogPage<LocationView> page) {
        return new CatalogPageResponse<>(page.items().stream().map(this::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
