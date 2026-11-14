package com.flighttripmanager.budgets.api.controller;
import java.net.URI;
import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import com.flighttripmanager.budgets.api.dto.*;
import com.flighttripmanager.budgets.api.mapper.*;
import com.flighttripmanager.budgets.application.port.in.BudgetManagement;
import com.flighttripmanager.budgets.application.contract.BudgetQuery;
import com.flighttripmanager.shared.api.error.ApiError;
@RestController @RequestMapping(value="/api/v1/trips/{tripId}",produces="application/json")
@Tag(name="Transport budgets",description="Explicit manual ledger. Actual replaces estimate for each expense in forecast. No automatic import of ticket totals or saved offers.")
@ApiResponses({
    @ApiResponse(responseCode="404",description="Trip or expense not found",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="409",description="Stale trip version or duplicate reference",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class BudgetController {
    private final BudgetManagement budgets;private final BudgetApiMapper mapper;private final BudgetJsonReader reader;
    public BudgetController(BudgetManagement budgets,BudgetApiMapper mapper,BudgetJsonReader reader){this.budgets=budgets;this.mapper=mapper;this.reader=reader;}
    @GetMapping("/budget")
    @Operation(summary="Get budget and expense totals",description="Current ledger totals, original currencies and missing conversions. remainingUsd is null when projected USD is incomplete. Category/leg caps are independent views, never added together.")
    @ApiResponse(responseCode="200",description="Budget summary",useReturnTypeSchema=true)
    public BudgetDto get(@PathVariable UUID tripId){return mapper.response(budgets.get(tripId));}
    @PutMapping(value="/budget",consumes="application/json")
    @Operation(summary="Replace total and category budgets",description="Requires version, totalBudgetUsd (nullable to clear) and categories object (empty clears category caps). Updates existing Trip.totalBudgetUsd.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(schema=@Schema(implementation=PutBudgetRequest.class))))
    @ApiResponse(responseCode="200",description="Updated budget",useReturnTypeSchema=true)
    public BudgetDto put(@PathVariable UUID tripId,@RequestBody String body){return mapper.response(budgets.put(tripId,mapper.command(reader.read(body,PutBudgetRequest.class))));}
    @GetMapping("/legs/{legId}/budget")
    @Operation(summary="Get leg budget",description="Only explicitly assigned expenses. Shared trip costs remain unallocated.")
    @ApiResponse(responseCode="200",description="Leg budget",useReturnTypeSchema=true)
    public LegBudgetDto leg(@PathVariable UUID tripId,@PathVariable UUID legId){return mapper.response(budgets.leg(tripId,legId));}
    @PutMapping(value="/legs/{legId}/budget",consumes="application/json")
    @Operation(summary="Replace leg cap",description="Requires version and amountUsd; explicit null clears the cap.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(schema=@Schema(implementation=PutLegBudgetRequest.class))))
    @ApiResponse(responseCode="200",description="Updated leg budget",useReturnTypeSchema=true)
    public LegBudgetDto putLeg(@PathVariable UUID tripId,@PathVariable UUID legId,@RequestBody String body){return mapper.response(budgets.putLeg(tripId,legId,mapper.command(reader.read(body,PutLegBudgetRequest.class))));}
    @GetMapping("/budget/legs")
    @Operation(summary="List leg budgets",description="Ordered by leg UUID. Includes legs without an assigned cap.")
    @ApiResponse(responseCode="200",description="Leg budgets page",useReturnTypeSchema=true)
    public BudgetPageDto<LegBudgetDto> legs(@PathVariable UUID tripId,@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return mapper.legs(budgets.legs(tripId,new BudgetQuery(page,size)));
    }
    @PostMapping(value="/expenses",consumes="application/json")
    @Operation(summary="Register transport expense",description="At least estimated or actual money is required. Optional legId must belong to trip and match transport category. referenceKey is an explicit trip-unique accounting key, not an inferred PNR.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(schema=@Schema(implementation=ExpenseRequest.class))))
    @ApiResponse(responseCode="201",description="Created expense",useReturnTypeSchema=true)
    public ResponseEntity<ExpenseDto> create(@PathVariable UUID tripId,@RequestBody String body){
        var result=mapper.response(budgets.createExpense(tripId,mapper.command(reader.read(body,ExpenseRequest.class))));
        return ResponseEntity.created(URI.create("/api/v1/trips/"+tripId+"/expenses/"+result.id())).body(result);
    }
    @PutMapping(value="/expenses/{id}",consumes="application/json")
    @Operation(summary="Replace expense",description="Full replacement of description, category, assignment, reference and both money values. Keep estimated when recording actual to compare both; forecast uses actual only.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(schema=@Schema(implementation=ExpenseRequest.class))))
    @ApiResponse(responseCode="200",description="Updated expense",useReturnTypeSchema=true)
    public ExpenseDto replace(@PathVariable UUID tripId,@PathVariable UUID id,@RequestBody String body){return mapper.response(budgets.replaceExpense(tripId,id,mapper.command(reader.read(body,ExpenseRequest.class))));}
    @GetMapping("/expenses/{id}")
    @Operation(summary="Read expense")
    @ApiResponse(responseCode="200",description="Expense",useReturnTypeSchema=true)
    public ExpenseDto expense(@PathVariable UUID tripId,@PathVariable UUID id){return mapper.response(budgets.expense(tripId,id));}
    @GetMapping("/expenses")
    @Operation(summary="List expenses",description="Newest first, UUID tie-break.")
    @ApiResponse(responseCode="200",description="Expense page",useReturnTypeSchema=true)
    public BudgetPageDto<ExpenseDto> expenses(@PathVariable UUID tripId,@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return mapper.expenses(budgets.expenses(tripId,new BudgetQuery(page,size)));
    }
    @DeleteMapping("/expenses/{id}")
    @Operation(summary="Delete expense",description="Explicitly removes this accounting item. Canceling a flight/leg never implies a refund.")
    @ApiResponse(responseCode="204",description="Deleted")
    public ResponseEntity<Void> delete(@PathVariable UUID tripId,@PathVariable UUID id,@RequestParam @Min(0) long version){
        budgets.deleteExpense(tripId,id,version);return ResponseEntity.noContent().build();
    }
}
