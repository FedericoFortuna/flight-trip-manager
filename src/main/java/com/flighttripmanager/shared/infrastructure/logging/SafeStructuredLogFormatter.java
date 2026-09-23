package com.flighttripmanager.shared.infrastructure.logging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/** Instantiated by Boot before the application context exists; no Spring injection required. */
public class SafeStructuredLogFormatter implements StructuredLogFormatter<ILoggingEvent> {
    private static final Set<String> CONTEXT_FIELDS = Set.of(
            "module", "operation", "provider", "entityId", "durationMs", "status", "errorCode", "requestId", "errorType");
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String format(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        fields.put("level", event.getLevel().toString());
        fields.put("logger", event.getLoggerName());
        fields.put("message", SensitiveDataMasker.mask(event.getFormattedMessage()));
        event.getMDCPropertyMap().forEach((key, value) -> addContext(fields, key, value));
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(pair -> addContext(fields, pair.key, pair.value));
        }
        // Exception messages/causes can contain credentials and third-party payloads.
        if (event.getThrowableProxy() != null) {
            fields.put("errorType", event.getThrowableProxy().getClassName());
        }
        try {
            return json.writeValueAsString(fields) + "\n";
        } catch (JsonProcessingException exception) {
            // Never fall back to raw event content when serialization fails.
            return "{\"level\":\"ERROR\",\"message\":\"Log serialization failed\"}\n";
        }
    }

    private void addContext(Map<String, Object> fields, String key, Object value) {
        if (!CONTEXT_FIELDS.contains(key) || value == null) {
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            fields.put(key, value);
        } else if (value instanceof String text) {
            fields.put(key, SensitiveDataMasker.mask(text));
        }
    }
}
