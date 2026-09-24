package com.flighttripmanager.catalog.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import com.flighttripmanager.catalog.application.contract.*;
import com.flighttripmanager.catalog.api.request.CatalogImportRequest;
import com.flighttripmanager.catalog.api.response.CatalogImportResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogImportApiMapper {
    CatalogImportBatch toBatch(CatalogImportRequest request);
    CatalogImportResponse toResponse(CatalogImportResult result);
}
