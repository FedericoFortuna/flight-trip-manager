package com.flighttripmanager.integration;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogImportIT {
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

    @BeforeEach void emptyCatalog() {
        jdbc.update("DELETE FROM catalog.airports");
        jdbc.update("DELETE FROM catalog.airlines");
        jdbc.update("DELETE FROM catalog.locations");
    }

    @Test void importsAllKindsNormalizesValuesAndReplaysWithoutWrites() throws Exception {
        var first = success(batch());
        assertThat(first.get("created").asInt()).isEqualTo(3);
        assertThat(first.get("updated").asInt()).isZero();
        var airport = get("/airports/AAA");
        assertThat(airport.get("name").asText()).isEqualTo("Example Airport");
        assertThat(airport.get("iataCode").asText()).isEqualTo("AAA");
        assertThat(airport.get("icaoCode").asText()).isEqualTo("AAAA");
        assertThat(airport.get("country").asText()).isEqualTo("AR");
        assertThat(airport.get("latitude").decimalValue()).isEqualByComparingTo("-34.1");
        assertThat(airport.get("lastSyncedAt").asText()).isNotEqualTo("2026-01-01T00:00:00Z");
        var snapshots = snapshots();
        var replay = success(batch());
        assertThat(replay.get("created").asInt()).isZero();
        assertThat(replay.get("updated").asInt()).isZero();
        assertThat(replay.get("unchanged").asInt()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            assertThat(replay.at("/items/" + i + "/id").asText()).isEqualTo(first.at("/items/" + i + "/id").asText());
        }
        assertThat(snapshots()).isEqualTo(snapshots);
        assertThat(jdbc.queryForObject("select source from catalog.airports", String.class)).isEqualTo("fixture");
        assertThat(jdbc.queryForObject("select external_id from catalog.airports", String.class)).isEqualTo("airport-1");
    }

    @Test void newerBatchPreservesIdentityAndCanExplicitlyDeactivateOrClearOptionalValues() throws Exception {
        var first = success(batch());
        var newer = batch().put("observedAt", "2026-02-01T00:00:00.123456Z");
        record(newer, "airports").put("iataCode", "AAB").put("active", false)
                .putNull("icaoCode").putNull("latitude").putNull("longitude");
        record(newer, "airlines").put("name", "Renamed Airline").put("active", false).putNull("icaoCode");
        record(newer, "locations").put("name", "Renamed City");
        var updated = success(newer);
        assertThat(updated.get("updated").asInt()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            assertThat(updated.at("/items/" + i + "/id").asText()).isEqualTo(first.at("/items/" + i + "/id").asText());
        }
        assertThat(get("/airports/AAB").get("active").asBoolean()).isFalse();
        assertThat(get("/airports/AAB").get("latitude").isNull()).isTrue();
        assertThat(get("/airports").get("totalElements").asInt()).isZero();
        assertThat(get("/airlines").get("totalElements").asInt()).isZero();
        assertThat(get("/airlines/A1").get("name").asText()).isEqualTo("Renamed Airline");
        assertThat(get("/locations").at("/items/0/name").asText()).isEqualTo("Renamed City");
        assertThat(success(newer).get("unchanged").asInt()).isEqualTo(3);
    }

    @Test void omissionsDoNotDeactivateOrDeleteExistingRows() throws Exception {
        success(batch());
        var partial = batch().put("observedAt", "2026-02-01T00:00:00Z");
        partial.remove("airports");
        partial.remove("locations");
        assertThat(success(partial).get("updated").asInt()).isEqualTo(1);
        assertThat(get("/airports/AAA").get("active").asBoolean()).isTrue();
        assertThat(get("/locations").get("totalElements").asInt()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"airports", "airlines", "locations"})
    void rejectsOlderVersionsAndConflictingReplays(String kind) throws Exception {
        success(batch());
        var conflicting = only(kind);
        record(conflicting, kind).put("name", "Conflicting name");
        error(conflicting, 409, "CATALOG_VERSION_CONFLICT");
        var stale = only(kind).put("observedAt", "2025-12-31T00:00:00Z");
        error(stale, 409, "CATALOG_STALE_DATA");
        assertThat(success(batch()).get("unchanged").asInt()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"airports", "airlines", "locations"})
    void neverAdoptsAnotherSourcesIdentityByMatchingCodeOrName(String kind) throws Exception {
        success(batch());
        error(only(kind).put("source", "different-source"), 409, "CATALOG_IDENTITY_CONFLICT");
        assertThat(success(batch()).get("unchanged").asInt()).isEqualTo(3);
    }

    @Test void lateConflictRollsBackEarlierInsertsAndReleasesLock() throws Exception {
        success(only("airlines"));
        error(batch().put("source", "another"), 409, "CATALOG_IDENTITY_CONFLICT");
        assertThat(get("/airports").get("totalElements").asInt()).isZero();
        assertThat(get("/locations").get("totalElements").asInt()).isZero();
        assertThat(success(batch()).get("created").asInt()).isEqualTo(2);
    }

    @Test void lateConflictRollsBackEarlierUpdates() throws Exception {
        success(batch());
        var old = snapshots();
        var failed = batch().put("observedAt", "2026-02-01T00:00:00Z");
        record(failed, "airports").put("name", "Should roll back");
        record(failed, "airlines").put("externalId", "different-id");
        error(failed, 409, "CATALOG_IDENTITY_CONFLICT");
        assertThat(snapshots()).isEqualTo(old);
        assertThat(success(batch()).get("unchanged").asInt()).isEqualTo(3);
    }

    @Test void legacyRowsArePreservedAndNeverAutomaticallyClaimed() throws Exception {
        jdbc.update("insert into catalog.airlines(id,iata_code,name,country,active) values (gen_random_uuid(),'A1','Legacy Airline','AR',true)");
        error(only("airlines"), 409, "CATALOG_IDENTITY_CONFLICT");
        assertThat(get("/airlines/A1").get("name").asText()).isEqualTo("Legacy Airline");
        assertThat(jdbc.queryForObject("select source from catalog.airlines", String.class)).isNull();
    }

    @Test void concurrentImportReturnsBusyAndCanBeRetriedAfterTransactionEnds() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("select pg_advisory_xact_lock(734021, 1)");
                error(batch(), 409, "CATALOG_IMPORT_BUSY");
                assertThat(get("/airports").get("totalElements").asInt()).isZero();
            } finally {
                connection.rollback();
            }
        }
        assertThat(success(batch()).get("created").asInt()).isEqualTo(3);
        assertThat(success(batch()).get("unchanged").asInt()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"airports", "airlines", "locations"})
    void duplicateExternalIdsAreRejectedBeforeAnyWrites(String kind) throws Exception {
        var duplicate = only(kind);
        var values = (com.fasterxml.jackson.databind.node.ArrayNode) duplicate.get(kind);
        var copy = record(duplicate, kind).deepCopy();
        copy.put("externalId", " " + copy.get("externalId").asText().strip() + " ");
        values.add(copy);
        error(duplicate, 400, "CATALOG_INVALID_BATCH");
        assertThat(snapshots()).allMatch(java.util.Map::isEmpty);
    }

    @Test void invalidLaterRecordRollsBackTheWholeBatchWithSafeError() throws Exception {
        var invalid = batch();
        record(invalid, "locations").put("country", "private-invalid-country");
        var response = error(invalid, 400, "CATALOG_INVALID_BATCH");
        assertThat(response.get("details").has("locations[0]")).isTrue();
        assertThat(response.toString()).doesNotContain("private-invalid-country");
        assertThat(snapshots()).allMatch(java.util.Map::isEmpty);
    }

    @ParameterizedTest @ValueSource(strings = {"", "1969-12-31T23:59:59Z", "2999-01-01T00:00:00Z", "2026-01-01T00:00:00.123456789Z"})
    void invalidVersionsAreRejected(String instant) throws Exception {
        assertThat(post(batch().put("observedAt", instant)).statusCode()).isEqualTo(400);
    }

    @ParameterizedTest @ValueSource(strings = {"", "bad/source", "bad source"})
    void invalidSourcesAreRejected(String source) throws Exception {
        error(batch().put("source", source), 400, "CATALOG_INVALID_BATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"root", "airports", "airlines", "locations"})
    void unknownFieldsAreRejectedInsteadOfSilentlyClearingData(String kind) throws Exception {
        var value = batch();
        (kind.equals("root") ? value : record(value, kind)).put("unknown-field", "private-value");
        var response = post(value);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("private-value");
    }

    @Test void batchesAreNonemptyAndBoundedAcrossKinds() throws Exception {
        var empty = batch();
        for (String kind : List.of("airports", "airlines", "locations")) empty.remove(kind);
        error(empty, 400, "CATALOG_INVALID_BATCH");
        var excessive = batch();
        var airports = (com.fasterxml.jackson.databind.node.ArrayNode) excessive.get("airports");
        for (int i = 1; i < 500; i++) airports.add(record(excessive, "airports").deepCopy().put("externalId", "other-" + i));
        error(excessive, 400, "CATALOG_INVALID_BATCH");
        assertThat(snapshots()).allMatch(java.util.Map::isEmpty);
    }

    @ParameterizedTest @ValueSource(strings = {
            "UPDATE catalog.airports SET source=NULL",
            "UPDATE catalog.airports SET external_id=NULL",
            "UPDATE catalog.airports SET source_observed_at=NULL",
            "UPDATE catalog.airports SET source='UPPERCASE'",
            "UPDATE catalog.airports SET external_id=' '",
            "UPDATE catalog.airports SET source_observed_at='2999-01-01T00:00:00Z'",
            "UPDATE catalog.airlines SET last_synced_at=NULL",
            "UPDATE catalog.locations SET source_observed_at='1969-01-01T00:00:00Z'"})
    void databaseEnforcesCompleteCanonicalProvenance(String sql) throws Exception {
        success(batch());
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void openApiDocumentsImportRequestResponseAndConflict() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var operation = json.readTree(response.body()).at("/paths/~1api~1v1~1catalog~1imports/post");
        assertThat(operation.at("/requestBody/content/application~1json/schema/$ref").asText()).endsWith("CatalogImportRequest");
        assertThat(operation.at("/responses/200/content/application~1json/schema/$ref").asText()).endsWith("CatalogImportResponse");
        for (String code : List.of("400", "409", "500")) {
            assertThat(operation.at("/responses/" + code + "/content/application~1json/schema/$ref").asText()).endsWith("ApiError");
        }
    }

    @Test void documentedStarterFileImportsAndReplaysThroughRealHttp() throws Exception {
        var example = (ObjectNode) json.readTree(java.nio.file.Files.readString(
                java.nio.file.Path.of("examples/catalog/argentina-starter.json")));
        assertThat(success(example).get("created").asInt()).isEqualTo(3);
        assertThat(success(example).get("unchanged").asInt()).isEqualTo(3);
        assertThat(get("/airports/EZE").get("icaoCode").asText()).isEqualTo("SAEZ");
        assertThat(get("/airlines/AR").get("icaoCode").asText()).isEqualTo("ARG");
    }

    @Test void migrationFromV2PreservesExistingIdentityAndData() {
        jdbc.execute("CREATE DATABASE catalog_upgrade");
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/catalog_upgrade";
        org.flywaydb.core.Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("2").load().migrate();
        var upgradeJdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        upgradeJdbc.update("""
                insert into catalog.airlines(id,iata_code,name,country,active,last_synced_at)
                values ('00000000-0000-0000-0000-000000000001','AA','Existing Airline','AR',true,'2026-01-01T00:00:00Z')
                """);
        upgradeJdbc.update("""
                insert into catalog.locations(id,type,name,city,country)
                values ('00000000-0000-0000-0000-000000000002','CITY','Existing City','Existing City','AR')
                """);
        var migrator = org.flywaydb.core.Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("3").load();
        assertThat(migrator.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgradeJdbc.queryForObject("select id::text from catalog.airlines", String.class))
                .isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(upgradeJdbc.queryForObject("select name from catalog.airlines", String.class)).isEqualTo("Existing Airline");
        assertThat(upgradeJdbc.queryForObject("select source from catalog.airlines", String.class)).isNull();
        assertThat(upgradeJdbc.queryForObject("select last_synced_at from catalog.locations", java.sql.Timestamp.class)).isNull();
        migrator.validate();
        assertThat(migrator.migrate().migrationsExecuted).isZero();
    }

    private ObjectNode only(String kind) throws Exception {
        var result = batch();
        for (String other : List.of("airports", "airlines", "locations")) if (!other.equals(kind)) result.remove(other);
        return result;
    }
    private ObjectNode record(ObjectNode batch, String kind) { return (ObjectNode) batch.get(kind).get(0); }
    private ObjectNode batch() throws Exception {
        return (ObjectNode) json.readTree("""
                {"source":" Fixture ","observedAt":"2026-01-01T00:00:00Z",
                 "airports":[{"externalId":" airport-1 ","iataCode":" aaa ","icaoCode":"aaaa","name":" Example Airport ",
                   "city":"Example City","country":" ar ","latitude":-34.1,"longitude":-58.2,"timezone":" UTC ","active":true}],
                 "airlines":[{"externalId":"airline-1","iataCode":"a1","icaoCode":"aaa","name":"Example Airline","country":"ar","active":true}],
                 "locations":[{"externalId":"location-1","type":"CITY","name":"Example City","city":"Example City","country":"ar"}]}
                """);
    }
    private List<java.util.Map<String, Object>> snapshots() {
        return List.of(snapshot("airports"), snapshot("airlines"), snapshot("locations"));
    }
    private java.util.Map<String, Object> snapshot(String table) {
        var rows = jdbc.queryForList("select * from catalog." + table);
        return rows.isEmpty() ? java.util.Map.of() : rows.get(0);
    }
    private JsonNode success(ObjectNode value) throws Exception {
        var response = post(value);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
    private JsonNode error(ObjectNode value, int status, String code) throws Exception {
        var response = post(value);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var body = json.readTree(response.body());
        assertThat(body.get("code").asText()).isEqualTo(code);
        return body;
    }
    private HttpResponse<String> post(ObjectNode value) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/catalog/imports"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode get(String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
}
