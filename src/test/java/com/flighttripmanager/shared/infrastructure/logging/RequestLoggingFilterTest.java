package com.flighttripmanager.shared.infrastructure.logging;

import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {
    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void generatesOwnIdAndRestoresThreadContextAfterSuccessfulRequest() throws Exception {
        MDC.setContextMap(Map.of("existing", "preserved"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/flights/private-pnr");
        request.setQueryString("api_key=private-secret");
        request.addHeader("Authorization", "Bearer private-token");
        request.addHeader("X-Request-Id", "untrusted-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new RequestLoggingFilter().doFilter(request, response, (req, res) -> {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/flights/{id}");
                assertThat(MDC.get("existing")).isNull();
                assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-Id"));
                MDC.put("errorCode", "RESOURCE_NOT_FOUND");
                response.setStatus(404);
            });
            assertThat(UUID.fromString(response.getHeader("X-Request-Id"))).isNotNull();
            assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("existing", "preserved"));
            String output = new SafeStructuredLogFormatter().format(appender.list.get(0));
            assertThat(output).contains("/api/v1/flights/{id}", "\"status\":404")
                    .doesNotContain("private-pnr", "private-secret", "private-token", "untrusted-id");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void clearsContextAndPropagatesUnhandledServletFailure() {
        assertThatThrownBy(() -> new RequestLoggingFilter().doFilter(
                new MockHttpServletRequest("GET", "/private-path"), new MockHttpServletResponse(),
                (request, response) -> { throw new ServletException("failure"); }))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
