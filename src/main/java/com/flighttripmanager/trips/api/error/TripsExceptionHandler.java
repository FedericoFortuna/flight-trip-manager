package com.flighttripmanager.trips.api.error;

import java.time.*;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.flighttripmanager.trips.application.contract.TripsException;
import com.flighttripmanager.shared.api.error.ApiError;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class TripsExceptionHandler {
    private final Clock clock;
    public TripsExceptionHandler(Clock clock) { this.clock = clock; }
    @ExceptionHandler(TripsException.class)
    public ResponseEntity<ApiError> handle(TripsException error) {
        int status = switch (error.reason()) {
            case TRIP_NOT_FOUND, LEG_NOT_FOUND -> 404;
            case INVALID_REQUEST, INVALID_REFERENCE -> 400;
            default -> 409;
        };
        String code = switch (error.reason()) {
            case TRIP_NOT_FOUND, LEG_NOT_FOUND -> error.reason().name();
            default -> "TRIP_" + error.reason().name();
        };
        MDC.put("errorCode", code);
        return ResponseEntity.status(status).body(new ApiError(code, "Trip operation could not be completed",
                Map.of(error.field(), status == 400 ? "Invalid value" : "Operation unavailable"), Instant.now(clock)));
    }
}
