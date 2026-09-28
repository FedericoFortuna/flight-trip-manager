package com.flighttripmanager.integration;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
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
@Testcontainers @ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(statements={"DELETE FROM flights.flight_history","DELETE FROM flights.flight_passengers","DELETE FROM flights.flight_segments",
    "DELETE FROM trips.passengers","DELETE FROM trips.trip_legs","DELETE FROM trips.trips","DELETE FROM catalog.airports","DELETE FROM catalog.airlines","DELETE FROM catalog.locations"})
@Sql("/catalog-fixtures.sql")
class FlightsIT {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl); r.add("spring.datasource.username",POSTGRES::getUsername); r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    record Setup(String trip,String leg,String passenger) { String collection() {return trip+"/legs/"+leg+"/flights";} }
    private Setup setup() throws Exception {
        var trip=ok("POST","/trips","{\"name\":\"Flight trip\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-10\"}",201);
        String path="/trips/"+trip.get("id").asText();
        var passenger=ok("POST",path+"/passengers","{\"version\":0,\"firstName\":\"Ana\",\"lastName\":\"García\"}",201);
        var leg=ok("POST",path+"/legs","""
            {"version":1,"origin":{"kind":"AIRPORT","id":"00000000-0000-0000-0000-000000000001"},
             "destination":{"kind":"AIRPORT","id":"00000000-0000-0000-0000-000000000002"},
             "transportType":"FLIGHT","departureDate":"2026-10-01"}
            """,201);
        return new Setup(path,leg.get("id").asText(),passenger.get("id").asText());
    }
    private ObjectNode body(Setup s,long version) throws Exception {
        ObjectNode root=(ObjectNode)json.readTree("""
            {"data":{"airlineId":"00000000-0000-0000-0000-000000000011","flightNumber":"A1123","flightDate":"2026-10-01",
              "originAirportId":"00000000-0000-0000-0000-000000000001","destinationAirportId":"00000000-0000-0000-0000-000000000002",
              "schedule":{"scheduledDeparture":"2026-10-02T01:00:00Z","scheduledArrival":"2026-10-02T04:00:00Z"},
              "operation":{"status":"SCHEDULED","departureGate":"12"},
              "booking":{"bookingReference":"ABC123","electronicTicketNumber":"1234567890123",
                "pricePaid":{"amount":125.25,"currency":"EUR"},"ticketTotal":{"amount":500,"currency":"USD"}},
              "passengers":[]}}
            """);
        root.put("version",version);
        ((ArrayNode)root.at("/data/passengers")).addObject().put("passengerId",s.passenger()).put("seat","12A").put("baggage","1 bag");
        return root;
    }
    @Test void roundTripCrudPreservesMoneyOriginalScheduleAndPassengerDetails() throws Exception {
        var s=setup(); var response=ok("POST",s.collection(),body(s,2).toString(),201);
        String path="/flights/"+response.get("id").asText();
        assertThat(response.get("tripVersion").asInt()).isEqualTo(3);
        assertThat(response.at("/data/booking/pricePaid/amountUsd").isNull()).isTrue();
        assertThat(response.at("/data/booking/ticketTotal/amountUsd").asText()).isEqualTo("500.0");
        assertThat(response.at("/data/passengers/0/seat").asText()).isEqualTo("12A");
        assertThat(response.get("provider").asText()).isEqualTo("MANUAL");
        assertThat(response.get("lastSyncedAt").isNull()).isTrue();
        var updated=ok("PATCH",path,"""
            {"version":3,"flightNumber":"a1124","schedule":{"scheduledDeparture":"2026-10-02T02:00:00Z","scheduledArrival":"2026-10-02T05:00:00Z"},
            "operation":{"status":"BOARDING","departureGate":"14","arrivalTerminal":"B"}}
            """,200);
        assertThat(updated.at("/data/flightNumber").asText()).isEqualTo("A1124");
        assertThat(updated.get("originalScheduledDeparture")).isEqualTo(response.get("originalScheduledDeparture"));
        assertThat(updated.at("/data/booking/bookingReference").asText()).isEqualTo("ABC123");
        assertThat(ok("GET",path,null,200)).isEqualTo(updated);
        var history=ok("GET",path+"/history?size=100",null,200);
        assertThat(history.toString()).contains("A1123","A1124","BOARDING","departureGate").doesNotContain("ABC123","1234567890123");
        ok("DELETE",path+"?version=4",null,204);
        error("GET",path,null,404,"FLIGHT_NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from flights.flight_history",Long.class)).isZero();
        assertThat(ok("GET",s.trip()+"/passengers",null,200).get("totalElements").asInt()).isEqualTo(1);
        assertThat(ok("GET",s.trip(),null,200).get("version").asInt()).isEqualTo(5);
    }
    @Test void optionalScheduleBookingAndAssignmentsCanBeCleared() throws Exception {
        var s=setup(); var flight=ok("POST",s.collection(),body(s,2).toString(),201); String p="/flights/"+flight.get("id").asText();
        var changed=ok("PATCH",p,"{\"version\":3,\"booking\":null,\"schedule\":null,\"passengers\":[]}",200);
        assertThat(changed.at("/data/booking/bookingReference").isNull()).isTrue();
        assertThat(changed.at("/data/schedule/scheduledDeparture").isNull()).isTrue();
        assertThat(changed.at("/data/passengers")).isEmpty();
        assertThat(changed.get("originalScheduledDeparture")).isEqualTo(flight.get("originalScheduledDeparture"));
        ok("DELETE",s.trip()+"/passengers/"+s.passenger()+"?version=4",null,204);
    }
    @Test void cancellationDoesNotCancelSiblingAndSamePnrDoesNotImplyProtection() throws Exception {
        var s=setup(); var first=ok("POST",s.collection(),body(s,2).toString(),201);
        var secondBody=body(s,3); ((ObjectNode)secondBody.get("data")).put("flightNumber","A1124");
        var second=ok("POST",s.collection(),secondBody.toString(),201);
        ok("PATCH","/flights/"+first.get("id").asText(),"{\"version\":4,\"operation\":{\"status\":\"CANCELLED\"}}",200);
        var other=ok("GET","/flights/"+second.get("id").asText(),null,200);
        assertThat(other.at("/data/operation/status").asText()).isEqualTo("SCHEDULED");
        assertThat(other.at("/data/connectionProtection").asText()).isEqualTo("UNKNOWN");
    }
    @Test void idempotentRetriesAndConcurrentCreatesDoNotDuplicateOrAdvanceRevision() throws Exception {
        var s=setup(); String body=body(s,2).toString(), key=UUID.randomUUID().toString();
        var first=client.sendAsync(request("POST",s.collection(),body,key),HttpResponse.BodyHandlers.ofString());
        var second=client.sendAsync(request("POST",s.collection(),body,key),HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first,second).join();
        assertThat(first.join().statusCode()).isEqualTo(201); assertThat(second.join().statusCode()).isEqualTo(201);
        assertThat(json.readTree(first.join().body()).get("id").asText()).isEqualTo(key);
        assertThat(jdbc.queryForObject("select count(*) from flights.flight_segments",Long.class)).isEqualTo(1);
        assertThat(ok("GET",s.trip(),null,200).get("version").asInt()).isEqualTo(3);
        var changed=body(s,3); ((ObjectNode)changed.get("data")).put("flightNumber","A1129");
        assertThat(client.send(request("POST",s.collection(),changed.toString(),key),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(409);
    }
    @Test void concurrentPatchesHaveOneWinnerAndOneHistoryRevision() throws Exception {
        var s=setup(); var flight=ok("POST",s.collection(),body(s,2).toString(),201); String p="/flights/"+flight.get("id").asText();
        var first=client.sendAsync(request("PATCH",p,"{\"version\":3,\"flightNumber\":\"A1124\"}",null),HttpResponse.BodyHandlers.ofString());
        var second=client.sendAsync(request("PATCH",p,"{\"version\":3,\"flightNumber\":\"A1125\"}",null),HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first,second).join();
        assertThat(List.of(first.join().statusCode(),second.join().statusCode())).containsExactlyInAnyOrder(200,409);
        assertThat(jdbc.queryForObject("select count(*) from flights.flight_history where field_name='flightNumber' and revision=4",Long.class)).isEqualTo(1);
        assertThat(ok("GET",s.trip(),null,200).get("version").asInt()).isEqualTo(4);
    }
    @Test void deletesAndTransportChangesAreRestrictedUntilFlightIsRemoved() throws Exception {
        var s=setup(); var flight=ok("POST",s.collection(),body(s,2).toString(),201);
        ok("DELETE",s.trip()+"/legs/"+s.leg()+"?version=3",null,409);
        ok("DELETE",s.trip()+"/passengers/"+s.passenger()+"?version=3",null,409);
        ok("PATCH",s.trip()+"/legs/"+s.leg(),"{\"version\":3,\"transportType\":\"TRAIN\"}",409);
        assertThat(ok("GET",s.trip(),null,200).get("version").asInt()).isEqualTo(3);
        ok("DELETE","/flights/"+flight.get("id").asText()+"?version=3",null,204);
        ok("PATCH",s.trip()+"/legs/"+s.leg(),"{\"version\":4,\"transportType\":\"TRAIN\"}",200);
        error("POST",s.collection(),body(s,5).toString(),400,"TRIP_INVALID_REFERENCE");
    }
    @Test void rejectsPassengerFromOtherTripWithoutPartialWrites() throws Exception {
        var s=setup(); var other=setup(); var body=body(s,2);
        ((ObjectNode)body.at("/data/passengers/0")).put("passengerId",other.passenger());
        ok("POST",s.collection(),body.toString(),400);
        assertThat(jdbc.queryForObject("select count(*) from flights.flight_segments",Long.class)).isZero();
        assertThat(ok("GET",s.trip(),null,200).get("version").asInt()).isEqualTo(2);
    }
    @Test void validatesLocalDepartureDateAndActiveCatalogButPreservesHistoricalReferences() throws Exception {
        var s=setup(); var bad=body(s,2); ((ObjectNode)bad.get("data")).put("flightDate","2026-10-02");
        error("POST",s.collection(),bad.toString(),400,"FLIGHT_INVALID_REFERENCE");
        var inactive=body(s,2); ((ObjectNode)inactive.get("data")).put("destinationAirportId","00000000-0000-0000-0000-000000000003");
        error("POST",s.collection(),inactive.toString(),400,"FLIGHT_INVALID_REFERENCE");
        var flight=ok("POST",s.collection(),body(s,2).toString(),201);
        jdbc.update("update catalog.airports set active=false where id='00000000-0000-0000-0000-000000000001'");
        ok("PATCH","/flights/"+flight.get("id").asText(),"{\"version\":3,\"operation\":{\"status\":\"DELAYED\"}}",200);
    }
    @ParameterizedTest @ValueSource(strings={
        "{}","[]","null","{",
        "{\"version\":2}","{\"version\":2.1,\"data\":{}}","{\"version\":\"2\",\"data\":{}}",
        "{\"version\":2,\"data\":null}","{\"version\":2,\"data\":{},\"unknown\":true}",
        "{\"version\":2,\"version\":2,\"data\":{}}","{\"version\":2,\"data\":{}} {}"
    })
    void invalidCreateBodiesDoNotWrite(String body) throws Exception {
        var s=setup(); ok("POST",s.collection(),body,400);
        assertThat(jdbc.queryForObject("select count(*) from flights.flight_segments",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={
        "{\"version\":3}","{\"version\":3,\"id\":\"x\"}","{\"version\":3,\"tripId\":\"x\"}",
        "{\"version\":3,\"operation\":null}","{\"version\":3,\"operation\":{\"departureGate\":\"B\"}}",
        "{\"version\":3,\"flightNumber\":null}","{\"version\":3,\"passengers\":{}}",
        "{\"version\":3,\"booking\":{\"pricePaid\":{\"amount\":-1,\"currency\":\"USD\"}}}",
        "{\"version\":3,\"booking\":{\"pricePaid\":{\"amount\":1.001,\"currency\":\"USD\"}}}",
        "{\"version\":3,\"booking\":{\"pricePaid\":{\"amount\":1,\"currency\":\"BAD\"}}}",
        "{\"version\":3,\"operation\":{\"status\":\"PROVIDER_STATUS\"}}",
        "{\"version\":3,\"schedule\":{\"scheduledDeparture\":\"2026-10-02T01:00:00.0000001Z\"}}"
    })
    void invalidPatchPreservesFlightAndHistory(String body) throws Exception {
        var s=setup(); var flight=ok("POST",s.collection(),body(s,2).toString(),201); String p="/flights/"+flight.get("id").asText();
        long history=jdbc.queryForObject("select count(*) from flights.flight_history",Long.class);
        ok("PATCH",p,body,400);
        assertThat(ok("GET",p,null,200)).isEqualTo(flight);
        assertThat(jdbc.queryForObject("select count(*) from flights.flight_history",Long.class)).isEqualTo(history);
    }
    @Test void listsFlightsAndHistoryWithPaginationAndMissingResourceErrors() throws Exception {
        var s=setup(); ok("POST",s.collection(),body(s,2).toString(),201);
        var second=ok("POST",s.collection(),body(s,3).toString(),201);
        var page=ok("GET",s.collection()+"?size=1",null,200);
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.at("/items/0/id")).isNotEqualTo(ok("GET",s.collection()+"?size=1&page=1",null,200).at("/items/0/id"));
        assertThat(ok("GET",s.collection()+"?page=100",null,200).get("items")).isEmpty();
        ok("GET",s.collection()+"?size=101",null,400);
        String p="/flights/"+second.get("id").asText();
        assertThat(ok("GET",p+"/history?size=1",null,200).get("totalElements").asInt()).isGreaterThan(1);
        ok("GET",p+"/history?page=-1",null,400);
        error("DELETE",p+"?version=3",null,409,"FLIGHT_VERSION_CONFLICT");
        error("PATCH",p,"{\"version\":3,\"flightNumber\":\"A1129\"}",409,"FLIGHT_VERSION_CONFLICT");
        String missing="/flights/"+UUID.randomUUID();
        for(String suffix:List.of("","/history"))error("GET",missing+suffix,null,404,"FLIGHT_NOT_FOUND");
        error("PATCH",missing,"{\"version\":0,\"flightNumber\":\"A1129\"}",404,"FLIGHT_NOT_FOUND");
        error("DELETE",missing+"?version=0",null,404,"FLIGHT_NOT_FOUND");
    }
    @Test void databaseConstraintsRejectCrossTripAssignmentsAndInvalidMoney() throws Exception {
        var s=setup(); var other=setup(); var flight=ok("POST",s.collection(),body(s,2).toString(),201);
        UUID id=UUID.fromString(flight.get("id").asText());
        assertThatThrownBy(()->jdbc.update("update flights.flight_passengers set passenger_id=? where flight_id=?",UUID.fromString(other.passenger()),id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update flights.flight_segments set price_paid_amount=-1 where id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update flights.flight_segments set destination_airport_id=origin_airport_id where id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update flights.flight_segments set status='ALIEN' where id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void sameIdempotencyKeyInDifferentTripsNeverOverwritesAnExistingRegistration() throws Exception {
        var firstSetup=setup(); var secondSetup=setup(); String key=UUID.randomUUID().toString();
        var first=client.sendAsync(request("POST",firstSetup.collection(),body(firstSetup,2).toString(),key),HttpResponse.BodyHandlers.ofString());
        var second=client.sendAsync(request("POST",secondSetup.collection(),body(secondSetup,2).toString(),key),HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first,second).join();
        assertThat(List.of(first.join().statusCode(),second.join().statusCode())).containsExactlyInAnyOrder(201,409);
        String winningTrip=first.join().statusCode()==201?firstSetup.trip():secondSetup.trip();
        var flight=ok("GET","/flights/"+key,null,200);
        assertThat("/trips/"+flight.get("tripId").asText()).isEqualTo(winningTrip);
        assertThat(jdbc.queryForObject("select sum(version) from trips.trips",Long.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from flights.flight_segments",Long.class)).isEqualTo(1);
    }
    @Test void exactPricesAndPassengerAssignmentsSurviveUpdatesWithoutAllocatingTicketTotals() throws Exception {
        var s=setup(); var body=body(s,2);
        ((ObjectNode)body.at("/data/booking/pricePaid")).put("amount",new java.math.BigDecimal("12345678901234567.89"));
        ((ObjectNode)body.at("/data/passengers/0")).putObject("pricePaid").put("amount",10).put("currency","USD");
        var response=client.send(request("POST",s.collection(),body.toString(),null),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        var result=json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(response.body());
        assertThat(result.at("/data/booking/pricePaid/amount").decimalValue()).isEqualByComparingTo("12345678901234567.89");
        assertThat(result.at("/data/passengers/0/pricePaid/amountUsd").decimalValue()).isEqualByComparingTo("10");
        String path="/flights/"+result.get("id").asText();
        ok("PATCH",path,"{\"version\":3,\"connectionProtection\":\"SEPARATE_TICKETS\"}",200);
        var updated=ok("PATCH",path,"{\"version\":4,\"connectionProtection\":\"SAME_TICKET\",\"passengers\":[{\"passengerId\":\""+s.passenger()+"\",\"seat\":\"14B\"}]}",200);
        assertThat(updated.at("/data/passengers/0/seat").asText()).isEqualTo("14B");
        assertThat(updated.at("/data/passengers/0/pricePaid").isNull()).isTrue();
        assertThat(updated.at("/data/booking/ticketTotal/amount").decimalValue()).isEqualByComparingTo("500");
    }
    @Test void unknownAndInactiveAirlinesAndMismatchedNumbersAreRejected() throws Exception {
        var s=setup();
        for(String airline:List.of(UUID.randomUUID().toString(),"00000000-0000-0000-0000-000000000013")){
            var body=body(s,2); ((ObjectNode)body.get("data")).put("airlineId",airline);
            error("POST",s.collection(),body.toString(),400,"FLIGHT_INVALID_REFERENCE");
        }
        var body=body(s,2); ((ObjectNode)body.get("data")).put("flightNumber","ZZ123");
        error("POST",s.collection(),body.toString(),400,"FLIGHT_INVALID_REFERENCE");
    }
    @Test void swaggerDocumentsAllSixOperations() throws Exception {
        var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
        var paths=json.readTree(response.body()).get("paths");
        var ops=Map.of("/api/v1/trips/{tripId}/legs/{legId}/flights",List.of("post","get"),"/api/v1/flights/{flightId}",List.of("get","patch","delete"),"/api/v1/flights/{flightId}/history",List.of("get"));
        for(var entry:ops.entrySet())for(String method:entry.getValue()){
            var operation=paths.get(entry.getKey()).get(method);
            assertThat(operation.get("summary").asText()).isNotBlank();
            for(String status:List.of("400","404","409","500"))assertThat(operation.get("responses").has(status)).isTrue();
        }
    }
    @Test void jsonTypesCannotCoerceNumbersIntoEnumsOrArrayDatesIntoIsoDates() throws Exception {
        var s=setup();
        var numericStatus=body(s,2);
        ((ObjectNode)numericStatus.at("/data/operation")).put("status",0);
        ok("POST",s.collection(),numericStatus.toString(),400);
        var numericProtection=body(s,2);
        ((ObjectNode)numericProtection.get("data")).put("connectionProtection",0);
        ok("POST",s.collection(),numericProtection.toString(),400);
        var arrayDate=body(s,2);
        ((ObjectNode)arrayDate.get("data")).putArray("flightDate").add(2026).add(10).add(1);
        ok("POST",s.collection(),arrayDate.toString(),400);
        var flight=ok("POST",s.collection(),body(s,2).toString(),201);
        String path="/flights/"+flight.get("id").asText();
        for(String patch:List.of(
            "{\"version\":3,\"flightDate\":[2026,10,1]}",
            "{\"version\":3,\"operation\":{\"status\":0}}",
            "{\"version\":3,\"booking\":{\"bookingReference\":123}}")) {
            ok("PATCH",path,patch,400);
        }
    }
    private void error(String method,String path,String body,int status,String code)throws Exception {
        assertThat(ok(method,path,body,status).get("code").asText()).isEqualTo(code);
    }
    private JsonNode ok(String method,String path,String body,int status)throws Exception{
        var response=client.send(request(method,path,body,null),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method+" "+path+": "+response.body()).isEqualTo(status);
        return response.body().isEmpty()?json.nullNode():json.readTree(response.body());
    }
    private HttpRequest request(String method,String path,String body,String key){
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        if(key!=null)builder.header("Idempotency-Key",key);
        return builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
    }
}
