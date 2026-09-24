package com.flighttripmanager.catalog.api.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import com.flighttripmanager.catalog.application.contract.LocationKind;

@Schema(description = "Explicit upsert batch. Maximum 500 records in total. Missing records are never deleted or deactivated.")
public record CatalogImportRequest(
        @Schema(description = "Stable source namespace, 1..64 ASCII letters/digits/dot/underscore/hyphen; normalized to lowercase") String source,
        @Schema(description = "Source observation time, UTC, not in the future, maximum microsecond precision; same timestamp must have identical normalized data") Instant observedAt,
        @Valid @Size(max = 500) List<AirportInput> airports,
        @Valid @Size(max = 500) List<AirlineInput> airlines,
        @Valid @Size(max = 500) List<LocationInput> locations) {

    @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown import field"); }

    public record AirportInput(String externalId, String iataCode, String icaoCode, String name, String city,
            String country, BigDecimal latitude, BigDecimal longitude, String timezone, Boolean active) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown airport field"); }
    }
    public record AirlineInput(String externalId, String iataCode, String icaoCode, String name, String country, Boolean active) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown airline field"); }
    }
    public record LocationInput(String externalId, LocationKind type, String name, String city, String country) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown location field"); }
    }
}
