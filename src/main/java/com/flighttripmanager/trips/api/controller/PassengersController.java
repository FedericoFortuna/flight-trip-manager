package com.flighttripmanager.trips.api.controller;

import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import com.flighttripmanager.shared.api.error.ApiError;
import com.flighttripmanager.trips.application.port.in.PassengerManagement;
import com.flighttripmanager.trips.application.contract.TripsQuery;
import com.flighttripmanager.trips.api.mapper.*;
import com.flighttripmanager.trips.api.request.*;
import com.flighttripmanager.trips.api.response.*;

@RestController
@RequestMapping(value = "/api/v1/trips/{tripId}/passengers", produces = "application/json")
@Tag(name = "Passengers", description = "Passengers belong to one trip. Mutations require and advance the trip version.")
@ApiResponses({
    @ApiResponse(responseCode = "404", description = "Trip or passenger not found in this trip", content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(responseCode = "409", description = "Stale trip version or integrity conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class PassengersController {
    private final PassengerManagement passengers;
    private final PassengerApiMapper mapper;
    private final TripPatchReader reader;
    public PassengersController(PassengerManagement passengers, PassengerApiMapper mapper, TripPatchReader reader) {
        this.passengers = passengers; this.mapper = mapper; this.reader = reader;
    }
    @PostMapping(consumes = "application/json")
    @Operation(summary = "Add a passenger", description = "Required current trip version and first/last names (trimmed, 1-100 characters). Optional notes up to 2000 characters. Names need not be unique. Identity documents are not supported.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = CreatePassengerRequest.class))))
    @ApiResponse(responseCode = "201", description = "Created passenger and new trip version", content = @Content(schema = @Schema(implementation = PassengerResponse.class)))
    public ResponseEntity<PassengerResponse> create(@PathVariable UUID tripId, @RequestBody String body) {
        return ResponseEntity.status(201).body(mapper.response(passengers.create(tripId, mapper.command(reader.createPassenger(body)))));
    }
    @GetMapping
    @Operation(summary = "List trip passengers", description = "Paginated by creation time and UUID ascending. Each item includes the trip version from the same database snapshot.")
    @ApiResponse(responseCode = "200", description = "Page of passengers", useReturnTypeSchema = true)
    public TripsPageResponse<PassengerResponse> list(@PathVariable UUID tripId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.page(passengers.list(tripId, new TripsQuery("", page, size)));
    }
    @PatchMapping(value = "/{passengerId}", consumes = {"application/json", "application/merge-patch+json"})
    @Operation(summary = "Patch a passenger", description = "Required trip version and at least one change. Omitted fields are preserved; null clears notes only. A passenger cannot be moved to another trip.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = PatchPassengerRequest.class))))
    @ApiResponse(responseCode = "200", description = "Updated passenger and new trip version", useReturnTypeSchema = true)
    public PassengerResponse patch(@PathVariable UUID tripId, @PathVariable UUID passengerId, @RequestBody String body) {
        return mapper.response(passengers.patch(tripId, passengerId, reader.passenger(body)));
    }
    @DeleteMapping("/{passengerId}")
    @Operation(summary = "Delete a passenger", description = "Required current trip version. No cascading deletes; references from other records prevent removal.")
    @ApiResponse(responseCode = "204", description = "Deleted; retrieve the trip to obtain its current version")
    public ResponseEntity<Void> delete(@PathVariable UUID tripId, @PathVariable UUID passengerId, @RequestParam @Min(0) long version) {
        passengers.delete(tripId, passengerId, version);
        return ResponseEntity.noContent().build();
    }
}
