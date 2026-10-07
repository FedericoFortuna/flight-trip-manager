package com.flighttripmanager.integration;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import testsupport.TripsClockConfiguration;
import static org.assertj.core.api.Assertions.*;
@Testcontainers @ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="flight-search.mode=MOCK")
@Import(TripsClockConfiguration.class)
@Sql(statements={"DELETE FROM flightsearch.search_history","DELETE FROM catalog.airports","DELETE FROM catalog.airlines","DELETE FROM catalog.locations"})
@Sql("/catalog-fixtures.sql")
class FlightSearchIT {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TripsClockConfiguration.MutableClock clock;
    final HttpClient client=HttpClient.newHttpClient();
    static final AtomicInteger sequence=new AtomicInteger();
    LocalDate date;
    @BeforeEach void reset(){clock.set(Instant.parse("2026-06-01T12:00:00Z"));date=LocalDate.of(2026,7,1).plusDays(sequence.incrementAndGet());}
    ObjectNode body(){
        var b=json.createObjectNode();b.put("origin","AAA");b.put("destination","AAB");b.put("departureDate",date.toString());b.put("adults",1);return b;
    }
    JsonNode call(String method,String path,String body,int expected) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20));
        if(body!=null)builder.header("Content-Type","application/json");
        var response=client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);return json.readTree(response.body());
    }
    JsonNode search(ObjectNode body) throws Exception{return call("POST","/api/v1/flight-search/search",body.toString(),200);}
    @Test void comparesSevenRoundTripsAndPersistsOnlyCriteria() throws Exception {
        var b=body();b.put("flexDays",3);b.put("returnDate",date.plusDays(10).toString());
        var result=search(b);
        assertThat(result.path("variants")).hasSize(7);assertThat(result.path("totalElements").asInt()).isEqualTo(21);
        assertThat(result.path("partial").asBoolean()).isFalse();assertThat(result.path("cached").asBoolean()).isFalse();
        for(var variant:result.path("variants"))
            assertThat(Duration.between(LocalDate.parse(variant.path("departureDate").asText()).atStartOfDay(),LocalDate.parse(variant.path("returnDate").asText()).atStartOfDay()).toDays()).isEqualTo(10);
        for(var item:result.path("items")){
            assertThat(item.at("/offer/synthetic").asBoolean()).isTrue();assertThat(item.at("/offer/testMode").asBoolean()).isTrue();
            assertThat(item.at("/offer/slices")).hasSize(2);assertThat(item.at("/offer/missingCosts")).isNotEmpty();
        }
        String stored=jdbc.queryForObject("select criteria::text from flightsearch.search_history",String.class);
        assertThat(stored).contains("departureDate","returnDate").doesNotContain("providerOfferId","total_amount","score","MOCK","expiresAt");
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_schema='flightsearch' and table_name='search_history'",String.class)).containsExactlyInAnyOrder("id","created_at","criteria");
    }
    @Test void normalizedCacheAndPaginationDoNotAddHistory() throws Exception {
        var b=body();b.put("flexDays",3);var first=search(b);
        b.put("origin"," aaa ");var repeated=search(b);
        assertThat(repeated.path("searchId")).isEqualTo(first.path("searchId"));assertThat(repeated.path("cached").asBoolean()).isTrue();
        var page=call("GET","/api/v1/flight-search/results/"+first.path("searchId").asText()+"?page=1&size=20",null,200);
        assertThat(page.path("items")).hasSize(1);assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from flightsearch.search_history",Long.class)).isEqualTo(1);
        var history=call("GET","/api/v1/flight-search/history?size=1",null,200);
        assertThat(history.path("totalElements").asInt()).isEqualTo(1);assertThat(history.at("/items/0/criteria/origin").asText()).isEqualTo("AAA");
    }
    @Test void expiredSnapshotReturnsGone() throws Exception {
        var result=search(body());clock.set(clock.instant().plusSeconds(181));
        var error=call("GET","/api/v1/flight-search/results/"+result.path("searchId").asText(),null,410);
        assertThat(error.path("code").asText()).isEqualTo("SEARCH_RESULTS_EXPIRED");
        call("GET","/api/v1/flight-search/results/"+UUID.randomUUID(),null,410);
    }
    @Test void filtersCheckedBagsAndUnmatchedAirlineWithoutInventingAvailability() throws Exception {
        var b=body();b.putObject("filters").put("checkedBagRequired",true);
        var result=search(b);assertThat(result.path("totalElements").asInt()).isEqualTo(2);
        for(var item:result.path("items"))assertThat(item.at("/offer/checkedBag").asText()).isEqualTo("INCLUDED");
        ((ObjectNode)b.path("filters")).putArray("airlines").add("A1");
        assertThat(search(b).path("totalElements").asInt()).isZero();
    }
    @Test void flexAtTodaySkipsPastDatesAndReportsPartial() throws Exception {
        var b=body();b.put("departureDate","2026-06-01");b.put("flexDays",3);
        var result=search(b);assertThat(result.path("partial").asBoolean()).isTrue();
        assertThat(result.toString()).contains("SKIPPED","DATE_OUTSIDE_SEARCH_WINDOW");assertThat(result.path("totalElements").asInt()).isEqualTo(12);
    }
    @ParameterizedTest @ValueSource(strings={"AAC","ZZZ"})
    void requiresActiveCatalogAirport(String destination) throws Exception {
        var b=body();b.put("destination",destination);call("POST","/api/v1/flight-search/search",b.toString(),400);
        assertThat(jdbc.queryForObject("select count(*) from flightsearch.search_history",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","null","{","{\"adults\":\"1\"}","{\"adults\":1.2}","{\"adults\":1,\"unknown\":true}","{\"adults\":1,\"adults\":2}","{\"adults\":1} {}"})
    void rejectsMalformedInput(String body) throws Exception {
        call("POST","/api/v1/flight-search/search",body,400);
        assertThat(jdbc.queryForObject("select count(*) from flightsearch.search_history",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"flexDays","adults","departureDate","returnDate","origin"})
    void rejectsInvalidDomainCriteria(String field) throws Exception {
        var b=body();
        switch(field){
            case "flexDays"->b.put(field,4);case "adults"->b.put(field,0);case "departureDate"->b.put(field,"2026-01-01");
            case "returnDate"->b.put(field,date.minusDays(1).toString());case "origin"->b.put(field,123);
        }
        call("POST","/api/v1/flight-search/search",b.toString(),400);
    }
    @ParameterizedTest @ValueSource(strings={"page=-1","size=0","size=101","page=1000001"})
    void validatesPagination(String query) throws Exception {
        call("POST","/api/v1/flight-search/search?"+query,body().toString(),400);
        call("GET","/api/v1/flight-search/history?"+query,null,400);
    }
    @Test void documentsThreeEndpointsWithoutPersistenceEntities() throws Exception {
        var docs=call("GET","/v3/api-docs",null,200);
        assertThat(docs.path("paths").has("/api/v1/flight-search/search")).isTrue();
        assertThat(docs.path("paths").has("/api/v1/flight-search/results/{searchId}")).isTrue();
        assertThat(docs.path("paths").has("/api/v1/flight-search/history")).isTrue();
        assertThat(docs.path("components").toString()).doesNotContain("SearchHistoryJpaEntity");
    }
}
