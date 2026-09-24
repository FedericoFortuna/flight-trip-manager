package com.flighttripmanager.trips.api.mapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import com.flighttripmanager.trips.application.contract.*;

/** HTTP-only presence/JSON parsing. Monetary input is never materialized as a floating-point value. */
@Component
public class TripPatchReader {
    private final ObjectMapper json;
    public TripPatchReader(ObjectMapper json) {
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    public PatchTrip trip(String body) {
        JsonNode node = object(body, Set.of("version", "name", "startDate", "endDate", "totalBudgetUsd", "manualStatusOverride"));
        return new PatchTrip(version(node), change(node, "name", String.class), change(node, "startDate", LocalDate.class),
                change(node, "endDate", LocalDate.class), change(node, "totalBudgetUsd", BigDecimal.class),
                change(node, "manualStatusOverride", TripStatusValue.class));
    }
    public PatchLeg leg(String body) {
        JsonNode node = object(body, Set.of("version", "origin", "destination", "transportType", "departureDate",
                "departureDateTime", "arrivalDate", "arrivalDateTime", "status"));
        return new PatchLeg(version(node), change(node, "origin", PlaceRef.class), change(node, "destination", PlaceRef.class),
                change(node, "transportType", TransportTypeValue.class), change(node, "departureDate", LocalDate.class),
                change(node, "departureDateTime", Instant.class), change(node, "arrivalDate", LocalDate.class),
                change(node, "arrivalDateTime", Instant.class), change(node, "status", LegStatusValue.class));
    }
    private JsonNode object(String body, Set<String> fields) {
        try {
            JsonNode node = json.readTree(body);
            if (node == null || !node.isObject() || node.size() < 2) throw invalid("request");
            var names = node.fieldNames();
            while (names.hasNext()) if (!fields.contains(names.next())) throw invalid("request");
            return node;
        } catch (IOException error) { throw invalid("request"); }
    }
    private static Long version(JsonNode node) {
        JsonNode value = node.get("version");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw invalid("version");
        return value.longValue();
    }
    private <T> Change<T> change(JsonNode node, String field, Class<T> type) {
        if (!node.has(field)) return Change.absent();
        JsonNode value = node.get(field);
        if (value.isNull()) return Change.of(null);
        if (type == BigDecimal.class ? !value.isNumber() : type == PlaceRef.class ? !value.isObject() : !value.isTextual()) {
            throw invalid(field);
        }
        try { return Change.of(json.treeToValue(value, type)); }
        catch (IOException error) { throw invalid(field); }
    }
    private static TripsException invalid(String field) {
        return new TripsException(TripsException.Reason.INVALID_REQUEST, field);
    }
}
