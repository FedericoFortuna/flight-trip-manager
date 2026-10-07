package com.flighttripmanager.flightsearch.api.error;
import java.time.Clock;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.flighttripmanager.shared.api.error.ApiError;
import com.flighttripmanager.flightsearch.application.contract.SavedOptionException;
@RestControllerAdvice @Order(Ordered.HIGHEST_PRECEDENCE)
public class SavedOptionExceptionHandler {
    private final Clock clock;
    public SavedOptionExceptionHandler(Clock clock){this.clock=clock;}
    @ExceptionHandler(SavedOptionException.class)
    public ResponseEntity<ApiError> handle(SavedOptionException error){
        int status=switch(error.reason()){
            case INVALID_REQUEST,OFFER_MISMATCH->400;case NOT_FOUND->404;case OFFER_UNAVAILABLE->410;default->409;
        };
        String code="SAVED_OPTION_"+error.reason().name();MDC.put("errorCode",code);
        return ResponseEntity.status(status).body(new ApiError(code,"Saved option operation could not be completed",Map.of(),clock.instant()));
    }
}
