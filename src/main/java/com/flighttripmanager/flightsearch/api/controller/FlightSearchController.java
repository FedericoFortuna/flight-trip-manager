package com.flighttripmanager.flightsearch.api.controller;
import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import com.flighttripmanager.shared.api.error.ApiError;
import com.flighttripmanager.flightsearch.application.port.in.FlightSearch;
import com.flighttripmanager.flightsearch.application.contract.SearchQuery;
import com.flighttripmanager.flightsearch.api.dto.*;
import com.flighttripmanager.flightsearch.api.mapper.*;
@RestController @RequestMapping(value="/api/v1/flight-search",produces="application/json")
@Tag(name="Flight search",description="Airline offers, independent of saved options and bookings. No agency scraping.")
public class FlightSearchController {
    private final FlightSearch searches;private final SearchApiMapper mapper;private final SearchJsonReader reader;
    public FlightSearchController(FlightSearch searches,SearchApiMapper mapper,SearchJsonReader reader){this.searches=searches;this.mapper=mapper;this.reader=reader;}
    @PostMapping(value="/search",consumes="application/json")
    @Operation(summary="Search one-way or return flights",description="Required IATA origin/destination, departureDate and adults (1-9). Optional returnDate, flexDays (0-3), childAges, cabin and filters. Return alternatives shift both dates together. Airports must exist and be active. Identical normalized criteria reuse temporary results; no new calls for cached pages. Prices are partial, grouped by currency; missing extras are explicit.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(schema=@Schema(implementation=CriteriaDto.class))))
    @ApiResponses({
        @ApiResponse(responseCode="200",description="Results with per-date outcomes and partial/test/synthetic flags",useReturnTypeSchema=true),
        @ApiResponse(responseCode="429",description="Search concurrency limit",content=@Content(schema=@Schema(implementation=ApiError.class))),
        @ApiResponse(responseCode="503",description="All provider attempts unavailable or disabled",content=@Content(schema=@Schema(implementation=ApiError.class)))
    })
    public SearchResultDto search(@RequestBody String body,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return mapper.response(searches.search(mapper.command(reader.read(body)),new SearchQuery(page,size)));
    }
    @GetMapping("/results/{searchId}")
    @Operation(summary="Page through temporary results",description="No provider calls. Results disappear after TTL, eviction or restart. Expired offers are removed; score remains from the original snapshot.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Page of results",useReturnTypeSchema=true),
        @ApiResponse(responseCode="410",description="Snapshot unavailable or expired",content=@Content(schema=@Schema(implementation=ApiError.class)))})
    public SearchResultDto results(@PathVariable UUID searchId,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return mapper.response(searches.results(searchId,new SearchQuery(page,size)));
    }
    @GetMapping("/history")
    @Operation(summary="List search criteria history",description="Only criteria and creation timestamp are persisted; no provider payloads or offers. Cached repetitions do not create a new history entry. New attempts, including unavailable providers, do.")
    @ApiResponse(responseCode="200",description="Criteria page ordered by creation descending and UUID",useReturnTypeSchema=true)
    public SearchPageDto<HistoryDto> history(@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return mapper.response(searches.history(new SearchQuery(page,size)));
    }
}
