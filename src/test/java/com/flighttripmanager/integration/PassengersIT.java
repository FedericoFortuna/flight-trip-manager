package com.flighttripmanager.integration;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(statements = {"DELETE FROM trips.passengers", "DELETE FROM trips.trip_legs", "DELETE FROM trips.trips"})
class PassengersIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Test void crudPreservesOmittedValuesClearsNotesAndAdvancesTripVersion() throws Exception {
        String trip = trip();
        var created = ok("POST", trip + "/passengers", create(0), 201);
        String path = trip + "/passengers/" + created.get("id").asText();
        assertThat(created.get("firstName").asText()).isEqualTo("María-José");
        assertThat(created.get("lastName").asText()).isEqualTo("O’Connor");
        assertThat(created.get("tripVersion").asInt()).isEqualTo(1);
        var changed = ok("PATCH", path, "{\"version\":1,\"firstName\":\"李\"}", 200);
        assertThat(changed.get("notes").asText()).isEqualTo("Vegetarian\nWindow preference");
        assertThat(changed.get("lastName")).isEqualTo(created.get("lastName"));
        assertThat(changed.get("createdAt")).isEqualTo(created.get("createdAt"));
        var cleared = ok("PATCH", path, "{\"version\":2,\"notes\":null,\"lastName\":\" García \"}", 200);
        assertThat(cleared.get("notes").isNull()).isTrue();
        assertThat(cleared.get("lastName").asText()).isEqualTo("García");
        assertThat(ok("GET", trip, null, 200).get("version").asInt()).isEqualTo(3);
        ok("DELETE", path + "?version=3", null, 204);
        assertThat(ok("GET", trip + "/passengers", null, 200).get("totalElements").asInt()).isZero();
        ok("DELETE", trip + "?version=4", null, 204);
    }
    @Test void duplicateNamesAreAllowedAndPaginationIsStableAndTripScoped() throws Exception {
        String trip = trip(), other = trip();
        ok("POST", trip + "/passengers", create(0), 201);
        ok("POST", trip + "/passengers", create(1), 201);
        ok("POST", other + "/passengers", create(0), 201);
        var first = ok("GET", trip + "/passengers?size=1", null, 200);
        var second = ok("GET", trip + "/passengers?size=1&page=1", null, 200);
        assertThat(first.get("totalElements").asInt()).isEqualTo(2);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(first.at("/items/0/id")).isNotEqualTo(second.at("/items/0/id"));
        assertThat(first.at("/items/0/tripVersion").asInt()).isEqualTo(2);
        assertThat(ok("GET", trip + "/passengers?page=100", null, 200).get("items")).isEmpty();
        assertThat(ok("GET", other + "/passengers", null, 200).get("totalElements").asInt()).isEqualTo(1);
    }
    @Test void passengerCannotBeReadOrMutatedThroughAnotherTrip() throws Exception {
        String trip = trip(), other = trip();
        var created = ok("POST", trip + "/passengers", create(0), 201);
        String wrongPath = other + "/passengers/" + created.get("id").asText();
        error("PATCH", wrongPath, "{\"version\":0,\"notes\":null}", 404, "PASSENGER_NOT_FOUND");
        error("DELETE", wrongPath + "?version=0", null, 404, "PASSENGER_NOT_FOUND");
        assertThat(ok("GET", other, null, 200).get("version").asInt()).isZero();
        assertThat(ok("GET", trip + "/passengers", null, 200).get("totalElements").asInt()).isEqualTo(1);
    }
    @Test void missingTripsAndPassengersHaveSafe404s() throws Exception {
        String missing = "/trips/" + UUID.randomUUID();
        error("GET", missing + "/passengers", null, 404, "TRIP_NOT_FOUND");
        error("POST", missing + "/passengers", create(0), 404, "TRIP_NOT_FOUND");
        String path = trip() + "/passengers/" + UUID.randomUUID();
        error("PATCH", path, "{\"version\":0,\"notes\":null}", 404, "PASSENGER_NOT_FOUND");
        error("DELETE", path + "?version=0", null, 404, "PASSENGER_NOT_FOUND");
    }
    @Test void parentDeleteIsRejectedUntilPassengersAreExplicitlyRemoved() throws Exception {
        String trip = trip();
        var passenger = ok("POST", trip + "/passengers", create(0), 201);
        var response = ok("DELETE", trip + "?version=1", null, 409);
        assertThat(response.at("/details/passengers").asText()).isEqualTo("Operation unavailable");
        assertThat(ok("GET", trip, null, 200).get("version").asInt()).isEqualTo(1);
        ok("DELETE", trip + "/passengers/" + passenger.get("id").asText() + "?version=1", null, 204);
        ok("DELETE", trip + "?version=2", null, 204);
    }
    @Test void staleVersionsCannotCreatePatchOrDelete() throws Exception {
        String trip = trip();
        var created = ok("POST", trip + "/passengers", create(0), 201);
        String path = trip + "/passengers/" + created.get("id").asText();
        error("POST", trip + "/passengers", create(0), 409, "TRIP_VERSION_CONFLICT");
        error("PATCH", path, "{\"version\":0,\"notes\":null}", 409, "TRIP_VERSION_CONFLICT");
        error("DELETE", path + "?version=0", null, 409, "TRIP_VERSION_CONFLICT");
        error("PATCH", trip, "{\"version\":0,\"name\":\"stale\"}", 409, "TRIP_VERSION_CONFLICT");
        ok("PATCH", trip, "{\"version\":1,\"name\":\"Changed trip\"}", 200);
        error("PATCH", path, "{\"version\":1,\"notes\":null}", 409, "TRIP_VERSION_CONFLICT");
    }
    @Test void concurrentPassengerCreatesCommitExactlyOneRevision() throws Exception {
        String trip = trip();
        var first = client.sendAsync(request("POST", trip + "/passengers", create(0)), HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request("POST", trip + "/passengers", create(0)), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).join();
        assertThat(List.of(first.join().statusCode(), second.join().statusCode())).containsExactlyInAnyOrder(201, 409);
        assertThat(ok("GET", trip + "/passengers", null, 200).get("totalElements").asInt()).isEqualTo(1);
        assertThat(ok("GET", trip, null, 200).get("version").asInt()).isEqualTo(1);
    }
    @Test void concurrentCreateAndParentDeletePreserveReferentialIntegrity() throws Exception {
        String trip = trip();
        var create = client.sendAsync(request("POST", trip + "/passengers", create(0)), HttpResponse.BodyHandlers.ofString());
        var delete = client.sendAsync(request("DELETE", trip + "?version=0", null), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(create, delete).join();
        if (create.join().statusCode() == 201) {
            assertThat(delete.join().statusCode()).isEqualTo(409);
        } else {
            assertThat(create.join().statusCode()).isEqualTo(404);
            assertThat(delete.join().statusCode()).isEqualTo(204);
        }
        assertThat(jdbc.queryForObject("select count(*) from trips.passengers p left join trips.trips t on p.trip_id=t.id where t.id is null", Long.class)).isZero();
    }
    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "[]", "null", "{",
        "{\"version\":0}", "{\"version\":0,\"firstName\":\"A\"}",
        "{\"version\":null,\"firstName\":\"A\",\"lastName\":\"B\"}",
        "{\"version\":-1,\"firstName\":\"A\",\"lastName\":\"B\"}",
        "{\"version\":0.5,\"firstName\":\"A\",\"lastName\":\"B\"}",
        "{\"version\":0,\"firstName\":null,\"lastName\":\"B\"}",
        "{\"version\":0,\"firstName\":123,\"lastName\":\"B\"}",
        "{\"version\":0,\"firstName\":\" \",\"lastName\":\"B\"}",
        "{\"version\":0,\"firstName\":\"A\",\"lastName\":\"B\",\"passport\":\"SECRET\"}",
        "{\"version\":0,\"firstName\":\"A\",\"firstName\":\"B\",\"lastName\":\"C\"}",
        "{\"version\":0,\"firstName\":\"A\",\"lastName\":\"B\"} {}"
    })
    void rejectsInvalidCreateWithoutWrites(String body) throws Exception {
        String trip = trip();
        var response = ok("POST", trip + "/passengers", body, 400);
        assertThat(response.toString()).doesNotContain("SECRET", "stackTrace");
        assertThat(ok("GET", trip, null, 200).get("version").asInt()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from trips.passengers", Long.class)).isZero();
    }
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"version\":1}", "{\"version\":1,\"firstName\":null}", "{\"version\":1,\"lastName\":\"\"}",
        "{\"version\":1,\"tripId\":\"other\"}", "{\"version\":1,\"notes\":123}",
        "{\"version\":1,\"notes\":{},\"id\":\"other\"}", "{\"notes\":null}"
    })
    void rejectsInvalidPatchAndPreservesExistingPassenger(String body) throws Exception {
        String trip = trip();
        var before = ok("POST", trip + "/passengers", create(0), 201);
        ok("PATCH", trip + "/passengers/" + before.get("id").asText(), body, 400);
        assertThat(ok("GET", trip + "/passengers", null, 200).at("/items/0")).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings = {"?size=0", "?size=101", "?page=-1", "?page=1000001", "?page=x"})
    void paginationBoundsAreEnforced(String query) throws Exception {
        ok("GET", trip() + "/passengers" + query, null, 400);
    }
    @Test void rejectsOverlongFieldsAndAcceptsBoundaries() throws Exception {
        String trip = trip();
        for (String field : List.of("firstName", "lastName", "notes")) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(create(0));
            body.put(field, "x".repeat(field.equals("notes") ? 2001 : 101));
            ok("POST", trip + "/passengers", body.toString(), 400);
        }
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(create(0));
        body.put("firstName", "x".repeat(100)); body.put("lastName", "y".repeat(100)); body.put("notes", "n".repeat(2000));
        ok("POST", trip + "/passengers", body.toString(), 201);
    }
    @Test void sqlConstraintsRejectOrphansInvalidNamesAndDangerousDeletes() throws Exception {
        String trip = trip();
        UUID tripId = UUID.fromString(trip.substring("/trips/".length()));
        var passenger = ok("POST", trip + "/passengers", create(0), 201);
        UUID id = UUID.fromString(passenger.get("id").asText());
        assertThatThrownBy(() -> jdbc.update("delete from trips.trips where id=?", tripId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update trips.passengers set trip_id=? where id=?", UUID.randomUUID(), id)).isInstanceOf(DataIntegrityViolationException.class);
        for (String invalid : List.of("", " ", "A\nB", "x".repeat(101))) {
            assertThatThrownBy(() -> jdbc.update("update trips.passengers set first_name=? where id=?", invalid, id)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("update trips.passengers set updated_at=created_at-interval '1 second' where id=?", id)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void openApiDocumentsFourOperationsAndNoIdentityDocumentFields() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var doc = json.readTree(response.body());
        String base = "/api/v1/trips/{tripId}/passengers";
        for (var entry : Map.of(base, List.of("post", "get"), base + "/{passengerId}", List.of("patch", "delete")).entrySet()) {
            for (String method : entry.getValue()) {
                var operation = doc.get("paths").get(entry.getKey()).get(method);
                assertThat(operation.get("summary").asText()).isNotBlank();
                assertThat(operation.get("description").asText()).isNotBlank();
                for (String status : List.of("400", "404", "409", "500")) assertThat(operation.get("responses").has(status)).isTrue();
            }
        }
        assertThat(doc.at("/components/schemas/CreatePassengerRequest/properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("version", "firstName", "lastName", "notes");
    }
    private String trip() throws Exception {
        var trip = ok("POST", "/trips", "{\"name\":\"Passengers trip\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-10\"}", 201);
        return "/trips/" + trip.get("id").asText();
    }
    private String create(long version) throws Exception {
        return json.writeValueAsString(Map.of("version", version, "firstName", " María-José ",
                "lastName", " O’Connor ", "notes", "Vegetarian\nWindow preference"));
    }
    private void error(String method, String path, String body, int status, String code) throws Exception {
        assertThat(ok(method, path, body, status).get("code").asText()).isEqualTo(code);
    }
    private JsonNode ok(String method, String path, String body, int status) throws Exception {
        var response = client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method + " " + path + ": " + response.body()).isEqualTo(status);
        return response.body().isEmpty() ? json.nullNode() : json.readTree(response.body());
    }
    private HttpRequest request(String method, String path, String body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
    }
}
