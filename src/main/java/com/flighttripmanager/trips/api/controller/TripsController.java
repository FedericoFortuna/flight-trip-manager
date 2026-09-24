package com.flighttripmanager.trips.api.controller;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import com.flighttripmanager.shared.api.error.ApiError;
import com.flighttripmanager.trips.application.port.in.TripManagement;
import com.flighttripmanager.trips.application.contract.TripsQuery;
import com.flighttripmanager.trips.api.mapper.*;
import com.flighttripmanager.trips.api.request.*;
import com.flighttripmanager.trips.api.response.*;

@RestController
@RequestMapping(value = "/api/v1/trips", produces = "application/json")
@Tag(name = "Trips", description = "Trips and legs. Mutations require the current aggregate version. Dates and status clock use UTC.")
@ApiResponses({
    @ApiResponse(responseCode = "404", description = "Trip or leg not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(responseCode = "409", description = "Stale version or integrity conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TripsController {
    private final TripManagement trips;
    private final TripApiMapper mapper;
    private final TripPatchReader patches;
    public TripsController(TripManagement trips, TripApiMapper mapper, TripPatchReader patches) {
        this.trips = trips; this.mapper = mapper; this.patches = patches;
    }
    @PostMapping(consumes = "application/json")
    @Operation(summary = "Create a trip", description = "Name and start/end dates are required. Budget and manual status override are optional.")
    @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TripResponse.class)))
    public ResponseEntity<TripResponse> create(@RequestBody CreateTripRequest request) {
        var response = mapper.response(trips.create(mapper.command(request)));
        return ResponseEntity.created(URI.create("/api/v1/trips/" + response.id())).body(response);
    }
    @GetMapping
    @Operation(summary = "List trips", description = "Literal case-insensitive name search. Sorted by creation time descending, UUID ascending.")
    @ApiResponse(responseCode = "200", description = "Page of trips", useReturnTypeSchema = true)
    public TripsPageResponse<TripResponse> list(@RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.trips(trips.list(new TripsQuery(q, page, size)));
    }
    @GetMapping("/{tripId}")
    @Operation(summary = "Get a trip", description = "Effective and derived statuses are calculated at read time. Legs have a separate paginated endpoint.")
    @ApiResponse(responseCode = "200", description = "Trip", useReturnTypeSchema = true)
    public TripResponse get(@PathVariable UUID tripId) { return mapper.response(trips.get(tripId)); }

    @PatchMapping(value = "/{tripId}", consumes = {"application/json", "application/merge-patch+json"})
    @Operation(summary = "Patch a trip", description = "Required version plus at least one changed field. Omitted values are preserved; null clears budget or manualStatusOverride.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = PatchTripRequest.class))))
    @ApiResponse(responseCode = "200", description = "Updated trip", useReturnTypeSchema = true)
    public TripResponse patch(@PathVariable UUID tripId, @RequestBody String body) {
        return mapper.response(trips.patch(tripId, patches.trip(body)));
    }
    @DeleteMapping("/{tripId}")
    @Operation(summary = "Delete an empty trip", description = "Trips containing legs are rejected; remove their legs explicitly first.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> delete(@PathVariable UUID tripId, @RequestParam @Min(0) long version) {
        trips.delete(tripId, version); return ResponseEntity.noContent().build();
    }
    @GetMapping("/{tripId}/legs")
    @Operation(summary = "List itinerary legs", description = "Manual order if present, otherwise date/time (unknown last), creation time and UUID.")
    @ApiResponse(responseCode = "200", description = "Page of legs", useReturnTypeSchema = true)
    public TripsPageResponse<LegResponse> legs(@PathVariable UUID tripId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.legs(trips.legs(tripId, new TripsQuery("", page, size)));
    }
    @PostMapping(value = "/{tripId}/legs", consumes = "application/json")
    @Operation(summary = "Add a leg", description = "Required parent version, origin/destination {kind,id}, transportType. Status defaults to PLANNED. Date-only or exact UTC instants; unknown dates require PLANNED. Maximum 500 legs.")
    @ApiResponse(responseCode = "201", description = "Created leg", content = @Content(schema = @Schema(implementation = LegResponse.class)))
    public ResponseEntity<LegResponse> addLeg(@PathVariable UUID tripId, @RequestBody CreateLegRequest request) {
        return ResponseEntity.status(201).body(mapper.response(trips.addLeg(tripId, mapper.command(request))));
    }
    @PatchMapping(value = "/{tripId}/legs/{legId}", consumes = {"application/json", "application/merge-patch+json"})
    @Operation(summary = "Patch a leg", description = "Required parent version plus changes. Changing an exact instant derives its UTC date unless a date is explicitly supplied. Clear the instant explicitly when switching to date-only.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = PatchLegRequest.class))))
    @ApiResponse(responseCode = "200", description = "Updated leg", useReturnTypeSchema = true)
    public LegResponse patchLeg(@PathVariable UUID tripId, @PathVariable UUID legId, @RequestBody String body) {
        return mapper.response(trips.patchLeg(tripId, legId, patches.leg(body)));
    }
    @DeleteMapping("/{tripId}/legs/{legId}")
    @Operation(summary = "Delete a leg", description = "Required parent version. Remaining manual positions are compacted.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> deleteLeg(@PathVariable UUID tripId, @PathVariable UUID legId, @RequestParam @Min(0) long version) {
        trips.deleteLeg(tripId, legId, version); return ResponseEntity.noContent().build();
    }
    @PatchMapping(value = "/{tripId}/legs/reorder", consumes = "application/json")
    @Operation(summary = "Reorder the complete itinerary", description = "Required version and all leg UUIDs exactly once. An empty orderedLegIds list restores automatic order; no orderingMode is stored.")
    @ApiResponse(responseCode = "200", description = "Updated trip version", useReturnTypeSchema = true)
    public TripResponse reorder(@PathVariable UUID tripId, @RequestBody ReorderLegsRequest request) {
        return mapper.response(trips.reorder(tripId, mapper.command(request)));
    }
}
