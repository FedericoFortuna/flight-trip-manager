package com.flighttripmanager.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(statements = {"DELETE FROM catalog.airports", "DELETE FROM catalog.airlines", "DELETE FROM catalog.locations"})
@Sql("/catalog-fixtures.sql")
class CatalogIT {
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

    @Test void airportDetailsPreserveCoordinatesTimezoneAndUtcTimestamp() throws Exception {
        var body = ok("/airports/aaa");
        assertThat(body.get("id").asText()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(body.get("iataCode").asText()).isEqualTo("AAA");
        assertThat(body.get("icaoCode").asText()).isEqualTo("AAAA");
        assertThat(body.get("latitude").decimalValue()).isEqualByComparingTo("-34.822222");
        assertThat(body.get("longitude").decimalValue()).isEqualByComparingTo("-58.535833");
        assertThat(body.get("timezone").asText()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(Instant.parse(body.get("lastSyncedAt").asText())).isEqualTo(Instant.parse("2026-09-23T12:00:00Z"));
        var unknown = ok("/airports/AAB");
        for (String field : List.of("icaoCode", "latitude", "longitude", "lastSyncedAt")) {
            assertThat(unknown.get(field).isNull()).as(field).isTrue();
        }
    }

    @Test void airportPaginationIsStableForIdenticalNames() throws Exception {
        var first = ok("/airports?size=1");
        var second = ok("/airports?page=1&size=1");
        assertThat(first.at("/items/0/iataCode").asText()).isEqualTo("AAA");
        assertThat(second.at("/items/0/iataCode").asText()).isEqualTo("AAB");
        assertThat(first.get("totalElements").asInt()).isEqualTo(2);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(first.get("page").asInt()).isZero();
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(ok("/airports?page=2&size=1").get("items")).isEmpty();
        assertThat(ok("/airports").get("size").asInt()).isEqualTo(20);
    }

    @ParameterizedTest
    @CsvSource({"airports,aaab,0", "airports,%20AaAa%20,1", "airports,example,1", "airports,ar,2",
            "airports,other,1", "airlines,a1,1", "airlines,aaa,1", "airlines,beta,1", "airlines,br,1",
            "locations,central,1", "locations,example,3", "locations,ar,3"})
    void searchesAcrossSupportedFields(String resource, String q, int count) throws Exception {
        assertThat(ok("/" + resource + "?q=" + q).get("totalElements").asInt()).isEqualTo(count);
    }

    @ParameterizedTest @ValueSource(strings = {"airports", "airlines", "locations"})
    void wildcardCharactersAreLiteral(String resource) throws Exception {
        int expected = resource.equals("locations") ? 1 : 0;
        assertThat(ok("/" + resource + "?q=%25_").get("totalElements").asInt()).isEqualTo(expected);
        assertThat(ok("/" + resource + "?q=%27%20OR%201%3D1").get("items")).isEmpty();
    }

    @Test void inactiveReferencesRemainAccessibleButAreExcludedFromDefaultLists() throws Exception {
        assertThat(ok("/airports?active=false").at("/items/0/iataCode").asText()).isEqualTo("AAC");
        assertThat(ok("/airports/AAC").get("active").asBoolean()).isFalse();
        assertThat(ok("/airlines?active=false").at("/items/0/iataCode").asText()).isEqualTo("A3");
        assertThat(ok("/airlines/a3").get("active").asBoolean()).isFalse();
        assertThat(ok("/airlines").get("totalElements").asInt()).isEqualTo(2);
        assertThat(ok("/airlines/a1").get("lastSyncedAt").asText()).isEqualTo("2026-09-23T12:00:00Z");
    }

    @ParameterizedTest @CsvSource({"CITY,21", "TRAIN_STATION,22", "BUS_STATION,23"})
    void locationsHaveGlobalIdentityAndDistinctTypes(String type, int suffix) throws Exception {
        var page = ok("/locations?type=" + type);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        String id = "00000000-0000-0000-0000-0000000000" + suffix;
        assertThat(page.at("/items/0/id").asText()).isEqualTo(id);
        var detail = ok("/locations/" + id);
        assertThat(detail.get("type").asText()).isEqualTo(type);
        assertThat(detail.has("tripId")).isFalse();
        assertThat(detail.has("latitude")).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"airports", "airlines", "locations"})
    void noMatchesYieldEmptyPages(String resource) throws Exception {
        var body = ok("/" + resource + "?q=notfound");
        assertThat(body.get("items")).isEmpty();
        assertThat(body.get("totalElements").asLong()).isZero();
        assertThat(body.get("totalPages").asLong()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"/airports/ZZZ,AIRPORT_NOT_FOUND", "/airlines/ZZ,AIRLINE_NOT_FOUND",
            "/locations/00000000-0000-0000-0000-000000000099,LOCATION_NOT_FOUND"})
    void missingResourcesUseStandardErrorContract(String path, String code) throws Exception {
        var response = get(path);
        assertThat(response.statusCode()).isEqualTo(404);
        var body = json.readTree(response.body());
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("details")).isEmpty();
        assertThat(Instant.parse(body.get("timestamp").asText())).isNotNull();
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/airports?page=-1", "/airports?page=1000001", "/airports?size=0", "/airports?size=101",
            "/airports?active=invalid", "/airports/AB", "/airports/A12", "/airlines/A", "/airlines/ABC",
            "/airlines?page=-1", "/airlines?size=101", "/locations?page=-1", "/locations?size=0",
            "/locations?type=AIRPORT", "/locations/not-a-uuid", "/locations?page=2147483648"})
    void malformedRequestsReturn400(String path) throws Exception {
        assertThat(get(path).statusCode()).as(path).isEqualTo(400);
    }

    @ParameterizedTest @ValueSource(strings = {"airports", "airlines", "locations"})
    void overlongSearchIsRejected(String resource) throws Exception {
        assertThat(get("/" + resource + "?q=" + "x".repeat(101)).statusCode()).isEqualTo(400);
    }

    @Test void noWriteEndpointsAreExposed() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(base() + "/airports"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.body()).contains("METHOD_NOT_ALLOWED");
    }

    @Test void openApiDocumentsAllSixOperationsAndErrors() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var paths = json.readTree(response.body()).get("paths");
        for (String path : List.of("/airports", "/airports/{iataCode}", "/airlines", "/airlines/{iataCode}", "/locations", "/locations/{id}")) {
            var operation = paths.get("/api/v1" + path).get("get");
            assertThat(operation.get("summary").asText()).isNotBlank();
            for (String status : List.of("200", "400", "500")) {
                assertThat(operation.get("responses").has(status)).as(path + " " + status).isTrue();
            }
            if (path.contains("{")) assertThat(operation.get("responses").has("404")).isTrue();
            String schema = operation.at("/responses/200/content/application~1json/schema/$ref").asText();
            assertThat(schema).as(path).contains("Response").doesNotContain("ApiError", "JpaEntity");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE catalog.airports SET iata_code='AAA' WHERE iata_code='AAB'",
            "UPDATE catalog.airports SET icao_code='AAAA' WHERE iata_code='AAB'",
            "UPDATE catalog.airports SET iata_code='abc' WHERE iata_code='AAB'",
            "UPDATE catalog.airports SET latitude=91, longitude=0 WHERE iata_code='AAB'",
            "UPDATE catalog.airports SET latitude=0, longitude=-181 WHERE iata_code='AAB'",
            "UPDATE catalog.airports SET latitude=0, longitude=NULL WHERE iata_code='AAB'",
            "UPDATE catalog.airports SET latitude=NULL, longitude=0 WHERE iata_code='AAB'",
            "UPDATE catalog.airports SET latitude='NaN', longitude=0 WHERE iata_code='AAB'",
            "UPDATE catalog.airports SET timezone=' ' WHERE iata_code='AAB'",
            "UPDATE catalog.airlines SET iata_code='A1' WHERE iata_code='A2'",
            "UPDATE catalog.airlines SET icao_code='AAA' WHERE iata_code='A2'",
            "UPDATE catalog.airlines SET iata_code='a2' WHERE iata_code='A2'",
            "UPDATE catalog.airlines SET name=' ' WHERE iata_code='A2'",
            "UPDATE catalog.airlines SET country='ar' WHERE iata_code='A2'",
            "UPDATE catalog.locations SET type='AIRPORT' WHERE type='CITY'",
            "UPDATE catalog.locations SET name='example city', city='example city', type='CITY' WHERE type='BUS_STATION'",
            "UPDATE catalog.locations SET name='' WHERE type='CITY'",
            "UPDATE catalog.locations SET country=NULL WHERE type='CITY'"})
    void databaseRejectsInvalidAndDuplicateCatalogData(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private JsonNode ok(String path) throws Exception {
        var response = get(path);
        assertThat(response.statusCode()).as(path + " " + response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
    private String base() { return "http://localhost:" + port + "/api/v1"; }
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
