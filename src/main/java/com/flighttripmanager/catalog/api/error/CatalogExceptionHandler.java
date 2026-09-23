package com.flighttripmanager.catalog.api.error;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.flighttripmanager.catalog.application.contract.CatalogNotFoundException;
import com.flighttripmanager.shared.api.error.ApiError;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CatalogExceptionHandler {
    private final Clock clock;
    public CatalogExceptionHandler(Clock clock) { this.clock = clock; }

    @ExceptionHandler(CatalogNotFoundException.class)
    public ResponseEntity<ApiError> notFound(CatalogNotFoundException exception) {
        MDC.put("errorCode", exception.code());
        return ResponseEntity.status(404).body(new ApiError(exception.code(),
                "Catalog resource not found", Map.of(), Instant.now(clock)));
    }
}
