package com.flighttripmanager.budgets.api.error;
import java.time.Clock;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.flighttripmanager.shared.api.error.ApiError;
import com.flighttripmanager.budgets.application.contract.BudgetException;
@RestControllerAdvice @Order(Ordered.HIGHEST_PRECEDENCE)
public class BudgetExceptionHandler {
    private final Clock clock;
    public BudgetExceptionHandler(Clock clock){this.clock=clock;}
    @ExceptionHandler(BudgetException.class)
    public ResponseEntity<ApiError> handle(BudgetException error){
        int status=switch(error.reason()){case INVALID_REQUEST,INVALID_LEG->400;case NOT_FOUND->404;default->409;};
        String code="BUDGET_"+error.reason().name();MDC.put("errorCode",code);
        return ResponseEntity.status(status).body(new ApiError(code,"Budget operation could not be completed",Map.of(),clock.instant()));
    }
}
