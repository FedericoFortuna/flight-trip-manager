package com.flighttripmanager.flightsearch.api.controller;
import java.net.URI;
import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import com.flighttripmanager.flightsearch.application.port.in.SavedOptions;
import com.flighttripmanager.flightsearch.application.contract.SearchQuery;
import com.flighttripmanager.flightsearch.api.dto.*;
import com.flighttripmanager.flightsearch.api.mapper.*;
import com.flighttripmanager.shared.api.error.ApiError;
@RestController @RequestMapping(value="/api/v1",produces="application/json")
@Tag(name="Saved flight options")
@ApiResponses({
    @ApiResponse(responseCode="404",description="Option or trip/leg not found",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="409",description="Revision conflict, full leg or closed alternatives",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="410",description="Source search or offer expired or unavailable",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class SavedOptionsController {
    private final SavedOptions options;private final SavedOptionApiMapper mapper;private final SavedOptionJsonReader reader;
    public SavedOptionsController(SavedOptions options,SavedOptionApiMapper mapper,SavedOptionJsonReader reader){
        this.options=options;this.mapper=mapper;this.reader=reader;
    }
    @PostMapping(value="/trips/{tripId}/legs/{legId}/saved-options",consumes="application/json")
    @Operation(summary="Save an observed offer",description="At most three retained options per FLIGHT leg with airport endpoints. Source offer must be live in the temporary search cache. Stores the whole offer and initial partial price; no provider calls. Replaying the same search/provider/offer returns the existing option.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(schema=@Schema(implementation=SaveOptionRequest.class))))
    @ApiResponse(responseCode="201",description="Saved option or existing same-provider offer",useReturnTypeSchema=true)
    public ResponseEntity<SavedOptionDto> save(@PathVariable UUID tripId,@PathVariable UUID legId,@RequestBody String body){
        var result=mapper.response(options.save(tripId,legId,mapper.command(reader.read(body))));
        return ResponseEntity.created(URI.create("/api/v1/saved-flight-options/"+result.id())).body(result);
    }
    @GetMapping("/trips/{tripId}/legs/{legId}/saved-options")
    @Operation(summary="List retained options, including inactive alternatives")
    @ApiResponse(responseCode="200",description="Page of retained options",useReturnTypeSchema=true)
    public SearchPageDto<SavedOptionDto> list(@PathVariable UUID tripId,@PathVariable UUID legId,
        @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return mapper.options(options.list(tripId,legId,new SearchQuery(page,size)));
    }
    @GetMapping("/saved-flight-options/{id}")
    @Operation(summary="Read durable offer snapshot",description="Remains readable when its source search expires. initialOffer is immutable and its old price is not a current quote.")
    @ApiResponse(responseCode="200",description="Saved option",useReturnTypeSchema=true)
    public SavedOptionDto get(@PathVariable UUID id){return mapper.response(options.get(id));}
    @DeleteMapping("/saved-flight-options/{id}")
    @Operation(summary="Delete an option and its price history",description="Requires current trip version; frees one of the three slots. Does not change reservations or reactivate alternatives.")
    @ApiResponse(responseCode="204",description="Deleted")
    public ResponseEntity<Void> delete(@PathVariable UUID id,@RequestParam @Min(0) long version){
        options.delete(id,version);return ResponseEntity.noContent().build();
    }
    @PostMapping(value="/saved-flight-options/{id}/price-observations",consumes="application/json")
    @ApiResponse(responseCode="200",description="Observed price or replay",useReturnTypeSchema=true)
    @Operation(summary="Append price from a new search result",description="Requires same provider, itinerary, passengers, cabin and baggage. Source selection replay is idempotent. Does not query external providers or overwrite initialOffer.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(schema=@Schema(implementation=SaveOptionRequest.class))))
    public PriceObservationDto observe(@PathVariable UUID id,@RequestBody String body){
        return mapper.response(options.observe(id,mapper.command(reader.read(body))));
    }
    @GetMapping("/saved-flight-options/{id}/price-history")
    @Operation(summary="List observed prices",description="Newest observations first; original currency is preserved and unknown fare/ancillary breakdown remains null. Results include test/synthetic flags.")
    @ApiResponse(responseCode="200",description="Page of observed prices",useReturnTypeSchema=true)
    public SearchPageDto<PriceSnapshotDto> history(@PathVariable UUID id,
        @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return mapper.prices(options.history(id,new SearchQuery(page,size)));
    }
}
