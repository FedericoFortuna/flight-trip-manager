package com.flighttripmanager.shared.api.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Covers servlet error dispatches that never reached controller advice. */
@Hidden
@RestController
public class ApiErrorController implements ErrorController {
    private final ApiErrorFactory errors;

    public ApiErrorController(ApiErrorFactory errors) {
        this.errors = errors;
    }

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(errors.forStatus(status));
    }
}
