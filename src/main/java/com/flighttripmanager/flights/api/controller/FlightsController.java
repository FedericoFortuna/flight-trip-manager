package com.flighttripmanager.flights.api.controller;
import java.net.URI;
import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import com.flighttripmanager.shared.api.error.ApiError;
import com.flighttripmanager.flights.application.contract.FlightQuery;
import com.flighttripmanager.flights.application.port.in.FlightManagement;
import com.flighttripmanager.flights.api.mapper.*;
import com.flighttripmanager.flights.api.request.*;
import com.flighttripmanager.flights.api.response.*;

@RestController
@RequestMapping(value="/api/v1", produces="application/json")
@Tag(name="Flights", description="Manual flight segments. Mutations use the trip version; no external provider calls.")
@ApiResponses({
    @ApiResponse(responseCode="404", description="Flight, trip or leg not found", content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="409", description="Stale trip version, conflicting idempotency key or integrity constraint", content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class FlightsController {
    private final FlightManagement flights;
    private final FlightApiMapper mapper;
    private final FlightJsonReader reader;
    public FlightsController(FlightManagement flights, FlightApiMapper mapper, FlightJsonReader reader) {
        this.flights=flights; this.mapper=mapper; this.reader=reader;
    }
    @PostMapping(value="/trips/{tripId}/legs/{legId}/flights", consumes="application/json")
    @Operation(summary="Register a flight segment", description="Required trip version and data: airlineId, flightNumber, flightDate in origin airport local time, originAirportId and destinationAirportId. Optional schedule, operation, booking, protection and up to 100 passenger assignments. Prices are independent observations, never summed or allocated automatically. Optional UUID Idempotency-Key becomes the flight ID; matching retries return the existing flight while it exists.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(schema=@Schema(implementation=CreateFlightRequest.class))))
    @ApiResponse(responseCode="201", description="Registered flight (or matching replay)", content=@Content(schema=@Schema(implementation=FlightViewResponse.class)))
    public ResponseEntity<FlightViewResponse> create(@PathVariable UUID tripId, @PathVariable UUID legId,
            @Parameter(description="Optional globally unique UUID for safe registration retries") @RequestHeader(value="Idempotency-Key", required=false) UUID key,
            @RequestBody String body) {
        var response=mapper.response(flights.create(tripId,legId,mapper.command(reader.create(body),key)));
        return ResponseEntity.created(URI.create("/api/v1/flights/"+response.id())).body(response);
    }
    @GetMapping("/flights/{flightId}")
    @Operation(summary="Get a registered flight", description="Includes passenger assignments, original schedule and current trip version. Provider is MANUAL and lastSyncedAt is null. PNR and ticket numbers are returned for local editing and never logged.")
    @ApiResponse(responseCode="200", description="Flight", useReturnTypeSchema=true)
    public FlightViewResponse get(@PathVariable UUID flightId) { return mapper.response(flights.get(flightId)); }
    @GetMapping("/trips/{tripId}/legs/{legId}/flights")
    @Operation(summary="List flights of a leg", description="Page ordered by flightDate, createdAt and UUID. Does not infer connections or chronological guarantees between passengers' different flights.")
    @ApiResponse(responseCode="200", description="Page of flights", useReturnTypeSchema=true)
    public FlightPageResponse<FlightViewResponse> list(@PathVariable UUID tripId,@PathVariable UUID legId,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return mapper.flights(flights.list(tripId,legId,new FlightQuery(page,size)));
    }
    @PatchMapping(value="/flights/{flightId}", consumes="application/json")
    @Operation(summary="Patch a flight", description="Required current trip version and changed top-level fields. Omitted fields are preserved. Supplied schedule, operation and booking objects replace the whole corresponding group; passengers replaces the entire assignment list. operation requires status. Null clears schedule, booking or passengers. Original scheduled instants remain unchanged. Status edits are explicit manual corrections recorded in history; cancellation affects only this segment.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(schema=@Schema(implementation=PatchFlightRequest.class))))
    @ApiResponse(responseCode="200", description="Updated flight and trip version", useReturnTypeSchema=true)
    public FlightViewResponse patch(@PathVariable UUID flightId,@RequestBody String body) {
        return mapper.response(flights.patch(flightId,reader.patch(body)));
    }
    @DeleteMapping("/flights/{flightId}")
    @Operation(summary="Delete a flight", description="Required trip version. Explicitly removes this flight's passenger links and history in one transaction. Does not delete passengers, other segments or the leg.")
    @ApiResponse(responseCode="204", description="Deleted")
    public ResponseEntity<Void> delete(@PathVariable UUID flightId,@RequestParam @Min(0) long version) {
        flights.delete(flightId,version); return ResponseEntity.noContent().build();
    }
    @GetMapping("/flights/{flightId}/history")
    @Operation(summary="List flight changes", description="Paginated flight number, status, terminal and gate history, ordered by trip revision, field and UUID. Contains no PNR or ticket numbers.")
    @ApiResponse(responseCode="200", description="Page of changes", useReturnTypeSchema=true)
    public FlightPageResponse<HistoryResponse> history(@PathVariable UUID flightId,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return mapper.history(flights.history(flightId,new FlightQuery(page,size)));
    }
}
