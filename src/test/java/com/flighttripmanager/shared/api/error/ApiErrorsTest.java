package com.flighttripmanager.shared.api.error;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import testsupport.ErrorProbeConfiguration.ProbeController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiErrorsTest {
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private final ApiErrorFactory errors = new ApiErrorFactory(Clock.fixed(NOW, ZoneOffset.UTC));
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ApiExceptionHandler(errors))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()
                                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @AfterEach
    void clearRequestContext() {
        org.slf4j.MDC.clear();
    }

    @Test
    void validationDoesNotExposeConstraintMessagesOrRejectedValues() throws Exception {
        mvc.perform(post("/api/v1/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.name").value("Invalid value"))
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()))
                .andExpect(content().string(not(containsString("ABC123"))))
                .andExpect(content().string(not(containsString("private-token"))));
    }

    @Test
    void malformedJsonIsSafe() throws Exception {
        mvc.perform(post("/api/v1/probe").contentType(MediaType.APPLICATION_JSON).content("{pnr=ABC123"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("ABC123"))));
    }

    @ParameterizedTest
    @CsvSource({"/api/v1/probe/lookup", "/api/v1/probe/lookup?date=secret-value", "/api/v1/probe/not-a-uuid"})
    void missingAndMalformedParametersAreSafe(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("secret-value"))));
    }

    @Test
    void methodNotAllowedPreservesAllowHeader() throws Exception {
        mvc.perform(delete("/api/v1/probe"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedRequestMediaTypeUsesSameContract() throws Exception {
        mvc.perform(post("/api/v1/probe").contentType(MediaType.TEXT_PLAIN).content("private-token"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unexpectedFailureHasNoInternalMessageOrTrace() throws Exception {
        mvc.perform(get("/api/v1/probe/failure")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("ABC123"))));
    }

    @ParameterizedTest
    @CsvSource({"400,INVALID_REQUEST", "404,RESOURCE_NOT_FOUND", "405,METHOD_NOT_ALLOWED",
            "406,NOT_ACCEPTABLE", "409,CONFLICT", "413,PAYLOAD_TOO_LARGE", "415,UNSUPPORTED_MEDIA_TYPE",
            "429,RATE_LIMIT_EXCEEDED", "503,SERVICE_UNAVAILABLE", "502,INTERNAL_ERROR", "401,REQUEST_REJECTED"})
    void servletDispatchHasSameContractWithoutExceptionDetails(int status, String code) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("private-token"));
        var response = new ApiErrorController(errors).error(request);
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().timestamp()).isEqualTo(NOW);
        assertThat(response.getBody().details()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"200", "600", "not-a-status"})
    void directErrorEndpointAndInvalidDispatchStatusDefaultTo500(String status) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,
                status.matches("[0-9]+") ? Integer.valueOf(status) : status);
        assertThat(new ApiErrorController(errors).error(request).getStatusCode().value()).isEqualTo(500);
        assertThat(new ApiErrorController(errors).error(new MockHttpServletRequest()).getStatusCode().value())
                .isEqualTo(500);
    }
}
