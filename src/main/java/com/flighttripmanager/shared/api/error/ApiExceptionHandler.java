package com.flighttripmanager.shared.api.error;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ApiErrorFactory errors;

    public ApiExceptionHandler(ApiErrorFactory errors) {
        this.errors = errors;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.put(error.getField(), "Invalid value"));
        if (fields.isEmpty()) {
            fields.put("request", "Invalid value");
        }
        return handleExceptionInternal(exception, errors.validation(fields), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ApiError error = body instanceof ApiError apiError ? apiError : errors.forStatus(status.value());
        MDC.put("errorCode", error.code());
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        // Keep Spring's committed-response handling and protocol headers (e.g. Allow).
        return super.handleExceptionInternal(exception, error, responseHeaders, status, request);
    }

    /** Last HTTP boundary only: deliberately converts unexpected failures to a safe 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        LOG.atError().addKeyValue("module", "shared").addKeyValue("operation", "http.error")
                .addKeyValue("errorCode", "INTERNAL_ERROR")
                .addKeyValue("errorType", exception.getClass().getName())
                .log("Unexpected request failure");
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatusCode.valueOf(500), request);
    }
}
