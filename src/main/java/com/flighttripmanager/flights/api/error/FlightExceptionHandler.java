package com.flighttripmanager.flights.api.error;
import java.time.*;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.flighttripmanager.flights.application.contract.FlightException;
import com.flighttripmanager.shared.api.error.ApiError;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class FlightExceptionHandler {
    private final Clock clock;
    public FlightExceptionHandler(Clock clock) { this.clock = clock; }
    @ExceptionHandler(FlightException.class)
    public ResponseEntity<ApiError> handle(FlightException error) {
        int status = switch (error.reason()) { case NOT_FOUND -> 404; case INVALID_REQUEST, INVALID_REFERENCE -> 400; default -> 409; };
        String code = "FLIGHT_" + error.reason().name(); MDC.put("errorCode", code);
        return ResponseEntity.status(status).body(new ApiError(code, "Flight operation could not be completed",
            Map.of(error.field(), status == 400 ? "Invalid value" : "Operation unavailable"), clock.instant()));
    }
}
