package com.flighttripmanager.flightsearch.api.error;
import java.time.Clock;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.flighttripmanager.shared.api.error.ApiError;
import com.flighttripmanager.flightsearch.application.contract.SearchException;
@Order(Ordered.HIGHEST_PRECEDENCE) @RestControllerAdvice
public class SearchExceptionHandler {
    private final Clock clock;public SearchExceptionHandler(Clock clock){this.clock=clock;}
    @ExceptionHandler(SearchException.class)
    public ResponseEntity<ApiError> handle(SearchException error){
        int status=switch(error.reason()){case INVALID_REQUEST,INVALID_REFERENCE->400;case RESULTS_EXPIRED->410;case BUSY->429;case PROVIDER_UNAVAILABLE->503;};
        String code="SEARCH_"+error.reason().name();MDC.put("errorCode",code);
        return ResponseEntity.status(status).body(new ApiError(code,"Search could not be completed",
            Map.of(error.field(),status==400?"Invalid value":"Operation unavailable"),clock.instant()));
    }
}
