package com.flighttripmanager.catalog.api.controller;

import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.flighttripmanager.shared.api.error.ApiError;
import com.flighttripmanager.catalog.application.contract.*;
import com.flighttripmanager.catalog.application.port.in.CatalogLookup;
import com.flighttripmanager.catalog.api.mapper.CatalogResponseMapper;
import com.flighttripmanager.catalog.api.response.*;

@RestController
@RequestMapping(value = "/api/v1", produces = "application/json")
@Tag(name = "Catalog", description = "Local catalog. Searches are literal and case-insensitive; fixed order: name, id.")
public class CatalogController {
    private final CatalogLookup catalog;
    private final CatalogResponseMapper mapper;

    public CatalogController(CatalogLookup catalog, CatalogResponseMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    @GetMapping("/airports")
    @Operation(summary = "Search local airports", description = "Search IATA, ICAO, name, city or country. active=true returns active airports; false returns inactive ones.")
    public CatalogPageResponse<AirportResponse> airports(
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "true") boolean active,
            @Parameter(description = "Zero-based page, maximum 1000000") @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.airports(catalog.airports(new CatalogQuery(q, page, size), active));
    }

    @GetMapping("/airports/{iataCode}")
    @Operation(summary = "Get an airport by IATA", description = "Case-insensitive. Includes inactive airports for historical references.")
    @ApiResponse(responseCode = "200", description = "Airport",
            content = @Content(schema = @Schema(implementation = AirportResponse.class)))
    @ApiResponse(responseCode = "404", description = "Airport not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AirportResponse airport(@PathVariable @Pattern(regexp = "[a-zA-Z]{3}") String iataCode) {
        return mapper.toResponse(catalog.airport(iataCode));
    }

    @GetMapping("/airlines")
    @Operation(summary = "Search local airlines", description = "Search IATA, ICAO, name or country. active=true returns active airlines; false returns inactive ones.")
    public CatalogPageResponse<AirlineResponse> airlines(
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "true") boolean active,
            @Parameter(description = "Zero-based page, maximum 1000000") @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.airlines(catalog.airlines(new CatalogQuery(q, page, size), active));
    }

    @GetMapping("/airlines/{iataCode}")
    @Operation(summary = "Get an airline by IATA", description = "Case-insensitive. Includes inactive airlines for historical references.")
    @ApiResponse(responseCode = "200", description = "Airline",
            content = @Content(schema = @Schema(implementation = AirlineResponse.class)))
    @ApiResponse(responseCode = "404", description = "Airline not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AirlineResponse airline(@PathVariable @Pattern(regexp = "[a-zA-Z0-9]{2}") String iataCode) {
        return mapper.toResponse(catalog.airline(iataCode));
    }

    @GetMapping("/locations")
    @Operation(summary = "Search reusable cities and stations", description = "Search name, city or country. Omit type to include all location kinds. Airports have their own catalog.")
    public CatalogPageResponse<LocationResponse> locations(
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(required = false) LocationKind type,
            @Parameter(description = "Zero-based page, maximum 1000000") @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.locations(catalog.locations(new CatalogQuery(q, page, size), type));
    }

    @GetMapping("/locations/{id}")
    @Operation(summary = "Get a location by UUID")
    @ApiResponse(responseCode = "200", description = "Location",
            content = @Content(schema = @Schema(implementation = LocationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Location not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public LocationResponse location(@PathVariable UUID id) {
        return mapper.toResponse(catalog.location(id));
    }
}
