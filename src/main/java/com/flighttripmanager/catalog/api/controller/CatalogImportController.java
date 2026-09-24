package com.flighttripmanager.catalog.api.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.flighttripmanager.catalog.application.port.in.CatalogImport;
import com.flighttripmanager.catalog.api.mapper.CatalogImportApiMapper;
import com.flighttripmanager.catalog.api.request.CatalogImportRequest;
import com.flighttripmanager.catalog.api.response.CatalogImportResponse;
import com.flighttripmanager.shared.api.error.ApiError;

@RestController
@RequestMapping(value = "/api/v1/catalog/imports", produces = "application/json")
@Tag(name = "Catalog import")
public class CatalogImportController {
    private final CatalogImport importer;
    private final CatalogImportApiMapper mapper;

    public CatalogImportController(CatalogImport importer, CatalogImportApiMapper mapper) {
        this.importer = importer;
        this.mapper = mapper;
    }

    @PostMapping(consumes = "application/json")
    @Operation(summary = "Import or synchronize a local catalog batch",
            description = "Atomic upsert by source + externalId + kind. Replays preserve UUIDs and timestamps. "
                    + "Omitted rows remain unchanged. Null optional fields clear existing values; active is required. "
                    + "No automatic adoption of existing IATA codes or locations from other sources.")
    @ApiResponse(responseCode = "200", description = "Committed batch with CREATED/UPDATED/UNCHANGED outcomes",
            content = @Content(schema = @Schema(implementation = CatalogImportResponse.class)))
    @ApiResponse(responseCode = "409", description = "Identity/version conflict, stale data or another import in progress; nothing committed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CatalogImportResponse importBatch(@Valid @RequestBody CatalogImportRequest request) {
        return mapper.toResponse(importer.importBatch(mapper.toBatch(request)));
    }
}
