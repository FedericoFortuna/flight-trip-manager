package com.flighttripmanager.integration;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import testsupport.TripsClockConfiguration;
import static org.assertj.core.api.Assertions.*;
@Testcontainers @ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="flight-search.mode=MOCK")
@Import(TripsClockConfiguration.class)
@Sql(statements={"DELETE FROM flightsearch.flight_price_snapshots","DELETE FROM flightsearch.saved_flight_options",
    "DELETE FROM flightsearch.search_history","DELETE FROM flights.flight_history","DELETE FROM flights.flight_passengers",
    "DELETE FROM flights.flight_segments","DELETE FROM trips.passengers","DELETE FROM trips.trip_legs","DELETE FROM trips.trips",
    "DELETE FROM catalog.airports","DELETE FROM catalog.airlines","DELETE FROM catalog.locations"})
@Sql("/catalog-fixtures.sql")
class SavedOptionsIT {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TripsClockConfiguration.MutableClock clock;
    final HttpClient client=HttpClient.newHttpClient();
    static final AtomicInteger SEQUENCE=new AtomicInteger();
    LocalDate date;
    @BeforeEach void reset(){clock.set(Instant.parse("2026-06-01T12:00:00Z"));date=LocalDate.of(2026,11,1).plusDays(SEQUENCE.incrementAndGet());}
    record Setup(String trip,String leg){String collection(){return "/trips/"+trip+"/legs/"+leg+"/saved-options";}String legPath(){return "/trips/"+trip+"/legs/"+leg;}}
    Setup setup() throws Exception {
        var trip=ok("POST","/trips","{\"name\":\"Compare flights\",\"startDate\":\"2026-11-01\",\"endDate\":\"2027-02-01\"}",201);
        var leg=ok("POST","/trips/"+trip.path("id").asText()+"/legs","""
            {"version":0,"origin":{"kind":"AIRPORT","id":"00000000-0000-0000-0000-000000000001"},
             "destination":{"kind":"AIRPORT","id":"00000000-0000-0000-0000-000000000002"},
             "transportType":"FLIGHT","departureDate":"%s"}
            """.formatted(date),201);
        return new Setup(trip.path("id").asText(),leg.path("id").asText());
    }
    JsonNode search(int flex) throws Exception {
        return ok("POST","/flight-search/search","{\"origin\":\"AAA\",\"destination\":\"AAB\",\"departureDate\":\""+date+"\",\"adults\":1,\"flexDays\":"+flex+"}",200);
    }
    ObjectNode selection(JsonNode search,int index,long version){
        var b=json.createObjectNode();b.put("version",version);b.put("searchId",search.path("searchId").asText());
        b.put("provider",search.at("/items/"+index+"/offer/provider").asText());b.put("providerOfferId",search.at("/items/"+index+"/offer/providerOfferId").asText());return b;
    }
    HttpRequest request(String method,String path,String body){
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(20));
        if(body!=null)b.header("Content-Type","application/json");
        return b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
    }
    JsonNode ok(String method,String path,String body,int status) throws Exception {
        var r=client.send(request(method,path,body),HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).as(r.body()).isEqualTo(status);
        return r.body().isBlank()?json.nullNode():json.readTree(r.body());
    }
    String path(JsonNode saved){return "/saved-flight-options/"+saved.path("id").asText();}
    long count(String table){return jdbc.queryForObject("select count(*) from flightsearch."+table,Long.class);}
    @Test void savesDurableOfferAndPartialPriceThenReadsAfterCacheExpires() throws Exception {
        var s=setup();var search=search(0);var saved=ok("POST",s.collection(),selection(search,0,1).toString(),201);
        assertThat(saved.path("tripVersion").asLong()).isEqualTo(2);assertThat(saved.path("active").asBoolean()).isTrue();
        assertThat(saved.at("/initialOffer/synthetic").asBoolean()).isTrue();
        var prices=ok("GET",path(saved)+"/price-history",null,200);assertThat(prices.path("totalElements").asInt()).isEqualTo(1);
        var p=prices.at("/items/0");assertThat(p.path("basePrice").isNull()).isTrue();assertThat(p.path("baggagePrice").isNull()).isTrue();
        assertThat(p.path("seatPrice").isNull()).isTrue();assertThat(p.path("originalCurrency").asText()).isEqualTo("USD");
        assertThat(p.path("amountUsd").decimalValue()).isEqualByComparingTo(p.path("originalAmount").decimalValue());
        clock.set(clock.instant().plusSeconds(181));ok("GET","/flight-search/results/"+search.path("searchId").asText(),null,410);
        assertThat(ok("GET",path(saved),null,200).path("initialOffer")).isEqualTo(saved.path("initialOffer"));
        // Persisted option does not depend on keeping the criteria history row.
        jdbc.update("delete from flightsearch.search_history");
        ok("GET",path(saved)+"/price-history",null,200);
        assertThat(ok("GET",s.collection()+"?size=1",null,200).path("totalElements").asInt()).isEqualTo(1);
    }
    @Test void threeRetainedSlotsAreEnforcedAndDeletionFreesOne() throws Exception {
        var s=setup();var search=search(0);var first=ok("POST",s.collection(),selection(search,0,1).toString(),201);
        ok("POST",s.collection(),selection(search,1,2).toString(),201);ok("POST",s.collection(),selection(search,2,3).toString(),201);
        var other=search(1);
        var error=ok("POST",s.collection(),selection(other,0,4).toString(),409);assertThat(error.path("code").asText()).isEqualTo("SAVED_OPTION_LIMIT_REACHED");
        ok("DELETE",path(first)+"?version=4",null,204);assertThat(count("flight_price_snapshots")).isEqualTo(2);
        var replacement=ok("POST",s.collection(),selection(other,0,5).toString(),201);assertThat(replacement.path("slot").asInt()).isEqualTo(1);
        assertThat(count("saved_flight_options")).isEqualTo(3);
    }
    @Test void confirmsBookingAtomicallyAndDoesNotReactivateOldOptionsOnReopen() throws Exception {
        var s=setup();var search=search(0);var saved=ok("POST",s.collection(),selection(search,0,1).toString(),201);
        ok("PATCH",s.legPath(),"{\"version\":2,\"status\":\"BOOKED\"}",200);
        var closed=ok("GET",path(saved),null,200);assertThat(closed.path("active").asBoolean()).isFalse();
        assertThat(closed.path("inactiveReason").asText()).isEqualTo("LEG_CLOSED");assertThat(closed.path("tripVersion").asInt()).isEqualTo(3);
        ok("POST",s.collection(),selection(search,1,3).toString(),409);
        ok("PATCH",s.legPath(),"{\"version\":3,\"status\":\"PLANNED\"}",200);
        assertThat(ok("GET",path(saved),null,200).path("active").asBoolean()).isFalse();
        ok("POST",s.collection(),selection(search,1,4).toString(),201);
        assertThat(count("flight_price_snapshots")).isEqualTo(2);
    }
    @Test void appendsNewObservationAndReplaysWithoutChangingHistoryOrInitialOffer() throws Exception {
        var s=setup();var search=search(0);var selected=selection(search,0,1);var saved=ok("POST",s.collection(),selected.toString(),201);
        var firstPrice=ok("POST",path(saved)+"/price-observations",selected.toString(),200);assertThat(firstPrice.path("replayed").asBoolean()).isTrue();
        clock.set(clock.instant().plusSeconds(181));var fresh=search(0);var observation=selection(fresh,0,2);
        var observed=ok("POST",path(saved)+"/price-observations",observation.toString(),200);assertThat(observed.path("replayed").asBoolean()).isFalse();
        assertThat(observed.path("tripVersion").asInt()).isEqualTo(3);
        var replay=ok("POST",path(saved)+"/price-observations",observation.toString(),200);
        assertThat(replay.path("snapshot")).isEqualTo(observed.path("snapshot"));assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(count("flight_price_snapshots")).isEqualTo(2);
        var page=ok("GET",path(saved)+"/price-history?size=1",null,200);assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.at("/items/0/sourceSearchId")).isEqualTo(fresh.path("searchId"));
        assertThat(ok("GET",path(saved),null,200).path("initialOffer")).isEqualTo(saved.path("initialOffer"));
        ok("POST",path(saved)+"/price-observations",selection(fresh,1,3).toString(),400);
        assertThat(count("flight_price_snapshots")).isEqualTo(2);
    }
    @Test void concurrentReplayCreatesOneOptionAndOnePrice() throws Exception {
        var s=setup();String body=selection(search(0),0,1).toString();
        var a=client.sendAsync(request("POST",s.collection(),body),HttpResponse.BodyHandlers.ofString());
        var b=client.sendAsync(request("POST",s.collection(),body),HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a,b).join();assertThat(a.join().statusCode()).isEqualTo(201);assertThat(b.join().statusCode()).isEqualTo(201);
        assertThat(json.readTree(a.join().body()).path("id")).isEqualTo(json.readTree(b.join().body()).path("id"));
        assertThat(count("saved_flight_options")).isEqualTo(1);assertThat(count("flight_price_snapshots")).isEqualTo(1);
    }
    @Test void concurrentDistinctSavesHaveOneRevisionWinner() throws Exception {
        var s=setup();var search=search(0);
        var a=client.sendAsync(request("POST",s.collection(),selection(search,0,1).toString()),HttpResponse.BodyHandlers.ofString());
        var b=client.sendAsync(request("POST",s.collection(),selection(search,1,1).toString()),HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a,b).join();assertThat(List.of(a.join().statusCode(),b.join().statusCode())).containsExactlyInAnyOrder(201,409);
        assertThat(count("saved_flight_options")).isEqualTo(1);
    }
    @Test void concurrentReservationAndSaveCannotLeaveActiveOptionInBookedLeg() throws Exception {
        var s=setup();var search=search(0);
        var a=client.sendAsync(request("POST",s.collection(),selection(search,0,1).toString()),HttpResponse.BodyHandlers.ofString());
        var b=client.sendAsync(request("PATCH",s.legPath(),"{\"version\":1,\"status\":\"BOOKED\"}"),HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a,b).join();
        if(b.join().statusCode()==200){assertThat(a.join().statusCode()).isEqualTo(409);}
        else {assertThat(b.join().statusCode()).isEqualTo(409);assertThat(a.join().statusCode()).isEqualTo(201);ok("PATCH",s.legPath(),"{\"version\":2,\"status\":\"BOOKED\"}",200);}
        assertThat(jdbc.queryForObject("select count(*) from flightsearch.saved_flight_options where active",Long.class)).isZero();
    }
    @Test void databaseConstraintsAndTripDeletionRestrictionsProtectOwnershipAndSlots() throws Exception {
        var s=setup();var saved=ok("POST",s.collection(),selection(search(0),0,1).toString(),201);var id=UUID.fromString(saved.path("id").asText());
        assertThatThrownBy(()->jdbc.update("update flightsearch.saved_flight_options set slot=4 where id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update flightsearch.saved_flight_options set trip_id=? where id=?",UUID.randomUUID(),id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update flightsearch.saved_flight_options set active=false where id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update flightsearch.flight_price_snapshots set amount_usd=null where saved_flight_option_id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
        ok("DELETE",s.legPath()+"?version=2",null,409);
        ok("PATCH",s.legPath(),"{\"version\":2,\"transportType\":\"TRAIN\"}",409);
        assertThat(ok("GET",path(saved),null,200).path("active").asBoolean()).isTrue();
        ok("DELETE",path(saved)+"?version=2",null,204);ok("DELETE",s.legPath()+"?version=3",null,204);
    }
    @Test void rejectsExpiredSourcesAndForgedProviderWithoutWrites() throws Exception {
        var s=setup();var search=search(0);var selection=selection(search,0,1);selection.put("provider","OTHER");
        ok("POST",s.collection(),selection.toString(),410);
        clock.set(clock.instant().plusSeconds(181));ok("POST",s.collection(),selection(search,0,1).toString(),410);
        assertThat(count("saved_flight_options")).isZero();
    }
    @Test void wrongRouteRejectedAndChangedRouteClosesExistingOption() throws Exception {
        var s=setup();var search=search(0);var saved=ok("POST",s.collection(),selection(search,0,1).toString(),201);
        ok("PATCH",s.legPath(),"""
            {"version":2,"origin":{"kind":"AIRPORT","id":"00000000-0000-0000-0000-000000000002"},
             "destination":{"kind":"AIRPORT","id":"00000000-0000-0000-0000-000000000001"}}
            """,200);
        assertThat(ok("GET",path(saved),null,200).path("inactiveReason").asText()).isEqualTo("ROUTE_CHANGED");
        ok("POST",s.collection(),selection(search,1,3).toString(),400);
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{","{\"version\":\"1\"}","{\"version\":1.2}","{\"version\":1,\"version\":2}"})
    void invalidJsonRejected(String body) throws Exception {var s=setup();ok("POST",s.collection(),body,400);assertThat(count("saved_flight_options")).isZero();}
    @ParameterizedTest @ValueSource(strings={"version","searchId","provider","providerOfferId","extra"})
    void rejectsInvalidFields(String field) throws Exception {
        var s=setup();var body=selection(search(0),0,1);
        switch(field){case "version"->body.put(field,-1);case "searchId"->body.put(field,"invalid");case "extra"->body.put(field,true);default->body.put(field,123);}
        ok("POST",s.collection(),body.toString(),400);assertThat(count("saved_flight_options")).isZero();
    }
    @Test void missingOptionsAndInvalidPaginationReturnSafeErrors() throws Exception {
        var s=setup();ok("GET","/saved-flight-options/"+UUID.randomUUID(),null,404);
        ok("GET",s.collection()+"?size=101",null,400);
        var saved=ok("POST",s.collection(),selection(search(0),0,1).toString(),201);
        ok("GET",path(saved)+"/price-history?page=-1",null,400);ok("DELETE",path(saved)+"?version=1",null,409);
        ok("DELETE",path(saved)+"?version=2",null,204);ok("GET",path(saved)+"/price-history",null,404);
    }
    @Test void roundTripKeepsFullPriceAndBothSlicesWithoutCreatingReturnLeg() throws Exception {
        var s=setup();
        var search=ok("POST","/flight-search/search","""
            {"origin":"AAA","destination":"AAB","departureDate":"%s","returnDate":"%s","adults":1}
            """.formatted(date,date.plusDays(10)),200);
        var saved=ok("POST",s.collection(),selection(search,0,1).toString(),201);
        assertThat(saved.at("/initialOffer/slices")).hasSize(2);
        assertThat(saved.at("/initialOffer/amount").decimalValue()).isEqualByComparingTo(search.at("/items/0/offer/amount").decimalValue());
        assertThat(jdbc.queryForObject("select count(*) from trips.trip_legs",Long.class)).isEqualTo(1);
    }
    @Test void nonFlightLegRejectedAndSearchingComparingRemainOpen() throws Exception {
        var s=setup();var search=search(0);
        ok("PATCH",s.legPath(),"{\"version\":1,\"transportType\":\"TRAIN\"}",200);
        ok("POST",s.collection(),selection(search,0,2).toString(),400);
        ok("PATCH",s.legPath(),"{\"version\":2,\"transportType\":\"FLIGHT\",\"status\":\"SEARCHING\"}",200);
        var saved=ok("POST",s.collection(),selection(search,0,3).toString(),201);
        ok("PATCH",s.legPath(),"{\"version\":4,\"status\":\"COMPARING\"}",200);
        assertThat(ok("GET",path(saved),null,200).path("active").asBoolean()).isTrue();
    }
    @Test void failsClosedObservationAfterReopenAndPreservesOldHistory() throws Exception {
        var s=setup();var search=search(0);var saved=ok("POST",s.collection(),selection(search,0,1).toString(),201);
        ok("PATCH",s.legPath(),"{\"version\":2,\"status\":\"BOOKED\"}",200);
        var fresh=search(1);
        ok("POST",path(saved)+"/price-observations",selection(fresh,0,3).toString(),409);
        ok("PATCH",s.legPath(),"{\"version\":3,\"status\":\"PLANNED\"}",200);
        var error=ok("POST",path(saved)+"/price-observations",selection(fresh,0,4).toString(),409);
        assertThat(error.path("code").asText()).isEqualTo("SAVED_OPTION_INACTIVE");
        assertThat(count("flight_price_snapshots")).isEqualTo(1);
    }
    @Test void swaggerDocumentsSelectionBodyAndPriceHistory() throws Exception {
        var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);var docs=json.readTree(response.body());
        assertThat(docs.path("paths").has("/api/v1/saved-flight-options/{id}/price-history")).isTrue();
        assertThat(docs.path("paths").has("/api/v1/trips/{tripId}/legs/{legId}/saved-options")).isTrue();
        assertThat(docs.path("components").toString()).contains("SaveOptionRequest","PriceSnapshotDto").doesNotContain("FlightPriceJpaEntity","SavedOptionJpaEntity");
    }
}
