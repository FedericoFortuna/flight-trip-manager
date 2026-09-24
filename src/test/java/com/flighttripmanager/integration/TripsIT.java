package com.flighttripmanager.integration;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import testsupport.TripsClockConfiguration;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("test")
@Import(TripsClockConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(statements = {"DELETE FROM trips.trip_legs", "DELETE FROM trips.trips", "DELETE FROM catalog.airports",
        "DELETE FROM catalog.airlines", "DELETE FROM catalog.locations"})
@Sql("/catalog-fixtures.sql")
class TripsIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired TripsClockConfiguration.MutableClock clock;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    @BeforeEach void resetClock() { clock.set(Instant.parse("2026-06-01T00:00:00Z")); }

    @Test void tripCrudSupportsExactBudgetPartialPatchAndExplicitNulls() throws Exception {
        var created = create();
        String path = path(created);
        assertThat(created.get("name").asText()).isEqualTo("Summer Trip");
        assertThat(created.get("status").asText()).isEqualTo("PLANNING");
        assertThat(created.get("version").asLong()).isZero();
        var patched = ok("PATCH", path, "{\"version\":0,\"totalBudgetUsd\":12345678901234567.89,\"manualStatusOverride\":\"BOOKED\"}", 200);
        assertThat(patched.get("totalBudgetUsd").asText()).isEqualTo("12345678901234567.89");
        assertThat(patched.get("status").asText()).isEqualTo("BOOKED");
        assertThat(patched.get("derivedStatus").asText()).isEqualTo("PLANNING");
        assertThat(patched.get("name").asText()).isEqualTo("Summer Trip");
        assertThat(patched.get("version").asLong()).isEqualTo(1);
        var cleared = ok("PATCH", path, "{\"version\":1,\"totalBudgetUsd\":null,\"manualStatusOverride\":null}", 200);
        assertThat(cleared.get("totalBudgetUsd").isNull()).isTrue();
        assertThat(cleared.get("status").asText()).isEqualTo("PLANNING");
        ok("DELETE", path + "?version=2", null, 204);
        error("GET", path, null, 404, "TRIP_NOT_FOUND");
    }

    @Test void tripSearchIsLiteralPaginatedAndStable() throws Exception {
        var first = create();
        create();
        var page = ok("GET", "/trips?q=%20SuMmEr%20&size=1", null, 200);
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        var next = ok("GET", "/trips?page=1&size=1", null, 200);
        assertThat(page.at("/items/0/id").asText()).isNotEqualTo(next.at("/items/0/id").asText());
        assertThat(ok("GET", "/trips?q=%25", null, 200).get("items")).isEmpty();
        assertThat(ok("GET", "/trips?page=100", null, 200).get("totalElements").asInt()).isEqualTo(2);
        assertThat(ok("GET", path(first), null, 200).get("legCount").asInt()).isZero();
    }

    @Test void legsReuseBothKindsOfCatalogReferencesAndEnforceParentScope() throws Exception {
        var trip = create();
        String path = path(trip);
        var leg = ok("POST", path + "/legs", leg(0, "2026-07-10", "BOOKED"), 201);
        assertThat(leg.at("/origin/kind").asText()).isEqualTo("AIRPORT");
        assertThat(leg.at("/destination/kind").asText()).isEqualTo("LOCATION");
        assertThat(leg.get("departureDateTime").isNull()).isTrue();
        assertThat(leg.get("status").asText()).isEqualTo("UPCOMING");
        assertThat(leg.get("tripVersion").asLong()).isEqualTo(1);
        assertThat(ok("GET", path, null, 200).get("status").asText()).isEqualTo("UPCOMING");
        var second = create();
        error("PATCH", path(second) + "/legs/" + leg.get("id").asText(), "{\"version\":0,\"status\":\"CANCELLED\"}", 404, "LEG_NOT_FOUND");
        error("DELETE", path + "?version=1", null, 409, "TRIP_TRIP_NOT_EMPTY");
        ok("DELETE", path + "/legs/" + leg.get("id").asText() + "?version=1", null, 204);
        ok("DELETE", path + "?version=2", null, 204);
    }

    @Test void unknownDepartureIsPlannedOnlyAndPatchingCanSwitchPrecision() throws Exception {
        String path = path(create());
        var unknown = ok("POST", path + "/legs", leg(0, null, "PLANNED"), 201);
        String legPath = path + "/legs/" + unknown.get("id").asText();
        error("PATCH", legPath, "{\"version\":1,\"status\":\"BOOKED\"}", 400, "TRIP_INVALID_REQUEST");
        var exact = ok("PATCH", legPath, "{\"version\":1,\"departureDateTime\":\"2026-07-10T23:00:00-03:00\",\"status\":\"BOOKED\"}", 200);
        assertThat(exact.get("departureDate").asText()).isEqualTo("2026-07-11");
        assertThat(exact.get("departureDateTime").asText()).isEqualTo("2026-07-11T02:00:00Z");
        var dateOnly = ok("PATCH", legPath, "{\"version\":2,\"departureDateTime\":null,\"departureDate\":\"2026-07-12\"}", 200);
        assertThat(dateOnly.get("departureDateTime").isNull()).isTrue();
        assertThat(dateOnly.get("departureDate").asText()).isEqualTo("2026-07-12");
        var noDate = ok("PATCH", legPath, "{\"version\":3,\"departureDate\":null,\"status\":\"PLANNED\"}", 200);
        assertThat(noDate.get("departureDate").isNull()).isTrue();
    }

    @Test void manualOrderingSupportsAtomicSwapAppendDeleteAndReset() throws Exception {
        String path = path(create());
        var late = ok("POST", path + "/legs", leg(0, "2026-07-12", "PLANNED"), 201);
        var early = ok("POST", path + "/legs", leg(1, "2026-07-10", "PLANNED"), 201);
        var unknown = ok("POST", path + "/legs", leg(2, null, "PLANNED"), 201);
        String a = late.get("id").asText(), b = early.get("id").asText(), c = unknown.get("id").asText();
        assertOrder(path, b, a, c);
        ok("PATCH", path + "/legs/reorder", reorder(3, c, a, b), 200);
        assertOrder(path, c, a, b);
        ok("PATCH", path + "/legs/reorder", reorder(4, b, c, a), 200);
        assertOrder(path, b, c, a);
        var appended = ok("POST", path + "/legs", leg(5, "2026-07-09", "PLANNED"), 201);
        assertThat(appended.get("manualOrder").asInt()).isEqualTo(3);
        ok("DELETE", path + "/legs/" + c + "?version=6", null, 204);
        var remaining = ok("GET", path + "/legs", null, 200);
        assertThat(remaining.at("/items/2/manualOrder").asInt()).isEqualTo(2);
        ok("PATCH", path + "/legs/reorder", reorder(7), 200);
        assertOrder(path, appended.get("id").asText(), b, a);
        assertThat(ok("GET", path + "/legs?size=1&page=1", null, 200).get("totalPages").asInt()).isEqualTo(3);
    }

    @Test void invalidReorderCannotPartiallyChangePositionsOrVersion() throws Exception {
        String path = path(create());
        var a = ok("POST", path + "/legs", leg(0, "2026-07-10", "PLANNED"), 201).get("id").asText();
        var b = ok("POST", path + "/legs", leg(1, "2026-07-11", "PLANNED"), 201).get("id").asText();
        error("PATCH", path + "/legs/reorder", reorder(2, a, a), 400, "TRIP_INVALID_REQUEST");
        error("PATCH", path + "/legs/reorder", reorder(2, a), 400, "TRIP_INVALID_REQUEST");
        error("PATCH", path + "/legs/reorder", reorder(2, a, UUID.randomUUID().toString()), 400, "TRIP_INVALID_REQUEST");
        assertOrder(path, a, b);
        assertThat(ok("GET", path, null, 200).get("version").asLong()).isEqualTo(2);
    }

    @Test void statusesRecomputeAsClockMovesAndOverrideDoesNotAlterLegs() throws Exception {
        String path = path(create());
        var payload = legNode(0, "2026-07-10", "BOOKED");
        payload.put("departureDateTime", "2026-07-10T10:00:00Z");
        payload.put("arrivalDateTime", "2026-07-10T12:00:00Z");
        ok("POST", path + "/legs", payload.toString(), 201);
        assertThat(ok("GET", path, null, 200).get("status").asText()).isEqualTo("UPCOMING");
        clock.set(Instant.parse("2026-07-10T11:00:00Z"));
        assertThat(ok("GET", path, null, 200).get("status").asText()).isEqualTo("IN_PROGRESS");
        clock.set(Instant.parse("2026-07-10T12:00:00Z"));
        assertThat(ok("GET", path, null, 200).get("status").asText()).isEqualTo("COMPLETED");
        var overridden = ok("PATCH", path, "{\"version\":1,\"manualStatusOverride\":\"CANCELLED\"}", 200);
        assertThat(overridden.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(overridden.get("derivedStatus").asText()).isEqualTo("COMPLETED");
        assertThat(ok("GET", path + "/legs", null, 200).at("/items/0/status").asText()).isEqualTo("COMPLETED");
    }

    @Test void staleMutationsAndConcurrentEditsCannotOverwriteNewerChanges() throws Exception {
        String path = path(create());
        var first = client.sendAsync(request("PATCH", path, "{\"version\":0,\"name\":\"First\"}"), HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request("PATCH", path, "{\"version\":0,\"name\":\"Second\"}"), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).join();
        assertThat(List.of(first.join().statusCode(), second.join().statusCode())).containsExactlyInAnyOrder(200, 409);
        assertThat(ok("GET", path, null, 200).get("version").asLong()).isEqualTo(1);
        error("POST", path + "/legs", leg(0, null, "PLANNED"), 409, "TRIP_VERSION_CONFLICT");
        error("DELETE", path + "?version=0", null, 409, "TRIP_VERSION_CONFLICT");
    }

    @ParameterizedTest @ValueSource(strings = {
            "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000099"})
    void newLegsRejectInactiveOrMissingAirportsWithoutBumpingVersion(String id) throws Exception {
        String path = path(create());
        var payload = legNode(0, null, "PLANNED");
        ((ObjectNode) payload.get("origin")).put("id", id);
        error("POST", path + "/legs", payload.toString(), 400, "TRIP_INVALID_REFERENCE");
        assertThat(ok("GET", path, null, 200).get("version").asLong()).isZero();
        assertThat(ok("GET", path + "/legs", null, 200).get("items")).isEmpty();
    }

    @Test void missingLocationsAndIdenticalEndpointsAreRejected() throws Exception {
        String path = path(create());
        var payload = legNode(0, null, "PLANNED");
        ((ObjectNode) payload.get("destination")).put("id", UUID.randomUUID().toString());
        error("POST", path + "/legs", payload.toString(), 400, "TRIP_INVALID_REFERENCE");
        payload.set("destination", payload.get("origin"));
        error("POST", path + "/legs", payload.toString(), 400, "TRIP_INVALID_REQUEST");
    }

    @Test void dateWindowCannotInvalidateExistingLegsAndFailedPatchRollsBack() throws Exception {
        String path = path(create());
        ok("POST", path + "/legs", leg(0, "2026-07-10", "PLANNED"), 201);
        error("PATCH", path, "{\"version\":1,\"name\":\"Should not persist\",\"endDate\":\"2026-07-05\"}", 400, "TRIP_INVALID_REQUEST");
        var unchanged = ok("GET", path, null, 200);
        assertThat(unchanged.get("name").asText()).isEqualTo("Summer Trip");
        assertThat(unchanged.get("version").asLong()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {
            "UPDATE trips.trips SET total_budget_usd=-1",
            "UPDATE trips.trips SET end_date='2026-06-01'",
            "UPDATE trips.trips SET version=-1",
            "UPDATE trips.trip_legs SET origin_airport_id=NULL",
            "UPDATE trips.trip_legs SET origin_location_id='00000000-0000-0000-0000-000000000021'",
            "UPDATE trips.trip_legs SET declared_status='BOOKED',departure_date=NULL",
            "UPDATE trips.trip_legs SET arrival_date='2026-06-30'",
            "UPDATE trips.trip_legs SET departure_date_time='2026-07-11T00:00:00Z'",
            "UPDATE trips.trip_legs SET manual_order=-1",
            "DELETE FROM catalog.airports WHERE iata_code='AAA'",
            "DELETE FROM catalog.locations WHERE type='CITY'",
            "DELETE FROM trips.trips"})
    void databaseProtectsDatesMoneyReferencesAndDeletion(String sql) throws Exception {
        String path = path(create());
        ok("POST", path + "/legs", leg(0, "2026-07-10", "PLANNED"), 201);
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"/trips?page=-1", "/trips?size=101", "/trips?page=1000001", "/trips/not-a-uuid"})
    void malformedQueriesUse400(String path) throws Exception {
        assertThat(send("GET", path, null).statusCode()).isEqualTo(400);
    }

    @Test void placeChangesValidateNewReferenceButHistoricalInactiveReferenceRemainsUsable() throws Exception {
        String path = path(create());
        var leg = ok("POST", path + "/legs", leg(0, "2026-07-10", "PLANNED"), 201);
        String legPath = path + "/legs/" + leg.get("id").asText();
        jdbc.update("update catalog.airports set active=false where iata_code='AAA'");
        ok("PATCH", legPath, "{\"version\":1,\"status\":\"SEARCHING\"}", 200);
        var changed = ok("PATCH", legPath, """
                {"version":2,"origin":{"kind":"LOCATION","id":"00000000-0000-0000-0000-000000000022"},
                 "destination":{"kind":"AIRPORT","id":"00000000-0000-0000-0000-000000000002"},"transportType":"TRAIN",
                 "arrivalDate":"2026-07-11"}
                """, 200);
        assertThat(changed.at("/origin/kind").asText()).isEqualTo("LOCATION");
        assertThat(changed.at("/destination/kind").asText()).isEqualTo("AIRPORT");
        assertThat(changed.get("transportType").asText()).isEqualTo("TRAIN");
        var dated = ok("PATCH", legPath, "{\"version\":3,\"arrivalDateTime\":\"2026-07-12T01:00:00Z\"}", 200);
        assertThat(dated.get("arrivalDate").asText()).isEqualTo("2026-07-12");
    }

    @Test void openApiDocumentsEveryTripsOperationAndPatchSchema() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var paths = json.readTree(response.body()).get("paths");
        for (String suffix : List.of("", "/{tripId}", "/{tripId}/legs", "/{tripId}/legs/{legId}", "/{tripId}/legs/reorder")) {
            assertThat(paths.has("/api/v1/trips" + suffix)).isTrue();
        }
        assertThat(paths.get("/api/v1/trips").get("post").get("responses").has("201")).isTrue();
        assertThat(paths.get("/api/v1/trips/{tripId}").get("get").get("responses").has("200")).isTrue();
        assertThat(paths.get("/api/v1/trips/{tripId}").get("delete").get("responses").has("204")).isTrue();
        assertThat(paths.get("/api/v1/trips/{tripId}").get("patch")
                .at("/requestBody/content/application~1json/schema/$ref").asText()).endsWith("PatchTripRequest");
    }

    private JsonNode create() throws Exception {
        return ok("POST", "/trips", "{\"name\":\" Summer Trip \",\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-31\",\"totalBudgetUsd\":100.25}", 201);
    }
    private static String path(JsonNode trip) { return "/trips/" + trip.get("id").asText(); }
    private String leg(long version, String date, String status) throws Exception { return legNode(version, date, status).toString(); }
    private ObjectNode legNode(long version, String date, String status) throws Exception {
        var node = (ObjectNode) json.readTree("""
                {"origin":{"kind":"AIRPORT","id":"00000000-0000-0000-0000-000000000001"},
                 "destination":{"kind":"LOCATION","id":"00000000-0000-0000-0000-000000000021"},"transportType":"FLIGHT"}
                """);
        node.put("version", version); node.put("departureDate", date); node.put("status", status); return node;
    }
    private String reorder(long version, String... ids) throws Exception {
        return json.writeValueAsString(Map.of("version", version, "orderedLegIds", ids));
    }
    private void assertOrder(String path, String... ids) throws Exception {
        List<String> actual = new ArrayList<>();
        ok("GET", path + "/legs", null, 200).get("items").forEach(node -> actual.add(node.get("id").asText()));
        assertThat(actual).containsExactly(ids);
    }
    private void error(String method, String path, String body, int status, String code) throws Exception {
        assertThat(ok(method, path, body, status).get("code").asText()).isEqualTo(code);
    }
    private JsonNode ok(String method, String path, String body, int status) throws Exception {
        var response = send(method, path, body);
        assertThat(response.statusCode()).as(method + " " + path + ": " + response.body()).isEqualTo(status);
        return response.body().isEmpty() ? json.nullNode()
                : json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(response.body());
    }
    private HttpRequest request(String method, String path, String body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
    }
}
