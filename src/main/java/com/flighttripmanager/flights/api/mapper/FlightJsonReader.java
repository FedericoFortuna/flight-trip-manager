package com.flighttripmanager.flights.api.mapper;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import com.flighttripmanager.flights.application.contract.*;
import com.flighttripmanager.flights.api.request.*;
@Component
public class FlightJsonReader {
    private static final Set<String> PATCH_FIELDS = Set.of("version", "airlineId", "flightNumber", "flightDate", "originAirportId", "destinationAirportId", "schedule", "operation", "booking", "connectionProtection", "passengers");
    private final ObjectMapper json;
    private final FlightApiMapper mapper;
    public FlightJsonReader(ObjectMapper json, FlightApiMapper mapper) {
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        this.mapper = mapper;
        // Jackson's scalar flag does not disable numeric/boolean-to-String coercion.
        for (var shape : List.of(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
                com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,
                com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean)) {
            this.json.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
                    .setCoercion(shape, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
        }
    }
    public CreateFlightRequest create(String body) {
        JsonNode node = object(body);
        version(node);
        try {
            CreateFlightRequest result = json.treeToValue(node, CreateFlightRequest.class);
            if (result.data() == null) throw invalid("data");
            if (!node.path("data").path("flightDate").isTextual()) throw invalid("flightDate");
            operation(result.data().operation());
            return result;
        } catch (IOException error) { throw invalid("request"); }
    }
    public FlightPatch patch(String body) {
        JsonNode node = object(body);
        if (node.size() < 2) throw invalid("request");
        node.fieldNames().forEachRemaining(field -> { if (!PATCH_FIELDS.contains(field)) throw invalid("request"); });
        var operation = change(node, "operation", OperationRequest.class);
        if (operation.present() && operation.value() == null) throw invalid("operation");
        operation(operation.value());
        return new FlightPatch(version(node),
            change(node, "airlineId", UUID.class), change(node, "flightNumber", String.class),
            change(node, "flightDate", LocalDate.class), change(node, "originAirportId", UUID.class),
            change(node, "destinationAirportId", UUID.class),
            map(change(node, "schedule", ScheduleRequest.class), mapper::data),
            map(operation, mapper::data), map(change(node, "booking", BookingRequest.class), mapper::data),
            change(node, "connectionProtection", ConnectionProtectionValue.class), passengers(node));
    }
    private FlightChange<List<TravelerData>> passengers(JsonNode node) {
        if (!node.has("passengers")) return FlightChange.absent();
        if (node.get("passengers").isNull()) return FlightChange.of(null);
        if (!node.get("passengers").isArray()) throw invalid("passengers");
        try {
            List<TravelerRequest> values = json.readerFor(new TypeReference<List<TravelerRequest>>() {}).readValue(node.get("passengers"));
            return FlightChange.of(mapper.passengers(values));
        } catch (IOException error) { throw invalid("passengers"); }
    }
    private static void operation(OperationRequest operation) {
        if (operation != null && operation.status() == null) throw invalid("operation.status");
    }
    private JsonNode object(String body) {
        try {
            JsonNode node = json.readTree(body);
            if (node == null || !node.isObject()) throw invalid("request");
            return node;
        } catch (IOException error) { throw invalid("request"); }
    }
    private static Long version(JsonNode node) {
        JsonNode value = node.get("version");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw invalid("version");
        return value.longValue();
    }
    private <T> FlightChange<T> change(JsonNode node, String field, Class<T> type) {
        if (!node.has(field)) return FlightChange.absent();
        if (node.get(field).isNull()) return FlightChange.of(null);
        if (type == LocalDate.class && !node.get(field).isTextual()) throw invalid(field);
        try { return FlightChange.of(json.treeToValue(node.get(field), type)); }
        catch (IOException error) { throw invalid(field); }
    }
    private static <T,R> FlightChange<R> map(FlightChange<T> input, java.util.function.Function<T,R> mapper) {
        return input.present() ? FlightChange.of(mapper.apply(input.value())) : FlightChange.absent();
    }
    private static FlightException invalid(String field) { return new FlightException(FlightException.Reason.INVALID_REQUEST, field); }
}
