package com.flighttripmanager.shared.infrastructure.logging;

import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.KeyValuePair;

import static org.assertj.core.api.Assertions.assertThat;

class SafeLoggingTest {
    @ParameterizedTest
    @ValueSource(strings = {"pnr=ABC123", "bookingReference: ABC123", "booking_reference='ABC123'",
            "electronicTicketNumber=ABC123", "electronic_ticket_number: ABC123", "ticket-number=ABC123",
            "api_key=ABC123", "X-API-Key: ABC123", "token=ABC123", "access_token=ABC123",
            "refreshToken=ABC123", "password=ABC123", "secret=ABC123", "Authorization: Bearer ABC123",
            "proxy-authorization=Basic ABC123", "Bearer ABC123", "Basic ABC123",
            "{\"pnr\":\"ABC123\"}", "PNR : \"ABC123 with spaces\""})
    void redactsSensitiveLabelledValues(String input) {
        assertThat(SensitiveDataMasker.mask(input)).doesNotContain("ABC123").contains("[REDACTED]");
    }

    @Test
    void preservesOperationalInformationAndHandlesNull() {
        assertThat(SensitiveDataMasker.mask("provider=mock status=ARRIVED")).isEqualTo("provider=mock status=ARRIVED");
        assertThat(SensitiveDataMasker.mask(null)).isNull();
    }

    @Test
    void formatterRedactsMessagesAndRestrictsStructuredContextAndExceptions() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setTimeStamp(0);
        event.setLevel(Level.ERROR);
        event.setLoggerName("test.logger");
        event.setMessage("failure pnr={}\nsecond line");
        event.setArgumentArray(new Object[]{"ABC123"});
        event.setMDCPropertyMap(Map.of("requestId", "safe-id", "authorization", "private-token"));
        event.addKeyValuePair(new KeyValuePair("module", "flights"));
        event.addKeyValuePair(new KeyValuePair("durationMs", 10L));
        event.addKeyValuePair(new KeyValuePair("status", true));
        event.addKeyValuePair(new KeyValuePair("provider", "pnr=ABC123"));
        event.addKeyValuePair(new KeyValuePair("entityId", new Object() {
            @Override public String toString() { return "private-object"; }
        }));
        event.addKeyValuePair(new KeyValuePair("errorCode", null));
        event.addKeyValuePair(new KeyValuePair("pnr", "private-pnr"));
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("private-exception")));

        String output = new SafeStructuredLogFormatter().format(event);
        var json = new ObjectMapper().readTree(output);
        assertThat(json.get("requestId").asText()).isEqualTo("safe-id");
        assertThat(json.get("module").asText()).isEqualTo("flights");
        assertThat(json.get("durationMs").asLong()).isEqualTo(10);
        assertThat(json.get("errorType").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(output).doesNotContain("ABC123", "private-token", "private-object", "private-pnr", "private-exception");
        assertThat(output.lines().count()).isEqualTo(1);
    }

    @Test
    void plainEventHasNoInventedErrorOrContextFields() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setLoggerName("test.logger");
        event.setMessage("Ready");
        event.setMDCPropertyMap(Map.of());
        var json = new ObjectMapper().readTree(new SafeStructuredLogFormatter().format(event));
        assertThat(json.has("errorType")).isFalse();
        assertThat(json.get("message").asText()).isEqualTo("Ready");
    }
}
