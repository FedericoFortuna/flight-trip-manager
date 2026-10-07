package com.flighttripmanager.flightsearch;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.flighttripmanager.flightsearch.application.port.out.*;
import com.flighttripmanager.flightsearch.domain.model.*;
import com.flighttripmanager.flightsearch.infrastructure.configuration.SearchProperties;
import com.flighttripmanager.flightsearch.infrastructure.providers.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class DuffelProviderTest {
    final ObjectMapper json=new ObjectMapper();
    final SearchProperties settings=new SearchProperties();
    final Clock clock=Clock.systemUTC();
    HttpServer server;ExecutorService executor;
    final AtomicInteger calls=new AtomicInteger();
    final AtomicReference<String> sent=new AtomicReference<>(),auth=new AtomicReference<>(),version=new AtomicReference<>();
    final AtomicReference<String> response=new AtomicReference<>("{\"data\":{\"live_mode\":false,\"offers\":[]}}");
    int status=200;boolean failFirst;String retryAfter;long delay;
    @BeforeEach void start() throws Exception {
        settings.setDuffelToken("test-fixture");settings.setRetryDelayMs(1);settings.setRequestTimeoutMs(2000);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/air/offer_requests",exchange->{
            int count=calls.incrementAndGet();sent.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));version.set(exchange.getRequestHeaders().getFirst("Duffel-Version"));
            try{
                if(delay>0)Thread.sleep(delay);
                if(retryAfter!=null)exchange.getResponseHeaders().set("Retry-After",retryAfter);
                byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(failFirst&&count==1?503:status,bytes.length);exchange.getResponseBody().write(bytes);
            }catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();}
        });
        server.start();
    }
    @AfterEach void stop(){server.stop(0);executor.shutdownNow();}
    DuffelHttp http(){return new DuffelHttp(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/air/offer_requests"),settings,clock);}
    FlightSearchProvider.Query query(){
        var c=new SearchCriteria("AAA","AAB",LocalDate.of(2026,10,1),null,0,1,List.of(),null,null);
        return new FlightSearchProvider.Query(c,c.variants().get(0),clock.instant().plusSeconds(20));
    }
    ObjectNode fixture() throws Exception {
        ObjectNode root=(ObjectNode)json.readTree("""
          {"data":{"live_mode":false,"offers":[{
            "id":"off_1","live_mode":false,"owner":{"iata_code":"ZZ"},"total_amount":"123.45","total_currency":"EUR",
            "slices":[{"segments":[{"origin":{"type":"airport","iata_code":"AAA","time_zone":"America/Argentina/Buenos_Aires"},
              "destination":{"type":"airport","iata_code":"AAB","time_zone":"UTC"},
              "departing_at":"2026-10-01T09:00:00","arriving_at":"2026-10-01T16:00:00",
              "marketing_carrier":{"iata_code":"ZZ"},"marketing_carrier_flight_number":"100",
              "operating_carrier":{"iata_code":"ZZ"},"stops":[],
              "passengers":[{"baggages":[{"type":"checked","quantity":1}]}]}]}]}]}}
          """);
        raw(root).put("expires_at",clock.instant().plusSeconds(180).toString());return root;
    }
    ObjectNode raw(ObjectNode root){return (ObjectNode)root.at("/data/offers/0");}
    ObjectNode segment(ObjectNode root){return (ObjectNode)root.at("/data/offers/0/slices/0/segments/0");}
    FlightSearchProvider.Batch parse(ObjectNode root){return new DuffelOfferReader(json,clock).read(root.toString(),query());}
    @Test void mapsContractTimezoneMoneyBaggageAndTestFlags() throws Exception {
        response.set(fixture().toString());
        var batch=new DuffelSearchProvider(http(),new DuffelOfferReader(json,clock),json).search(query());
        assertThat(batch.offers()).hasSize(1);var offer=batch.offers().get(0);
        assertThat(offer.amount()).isEqualByComparingTo("123.45");assertThat(offer.amountUsd()).isNull();
        assertThat(offer.durationMinutes()).isEqualTo(240);assertThat(offer.checkedBag()).isEqualTo(BagAvailability.INCLUDED);
        assertThat(offer.testMode()).isTrue();assertThat(offer.synthetic()).isFalse();
        assertThat(offer.slices().get(0).segments().get(0).departingAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(auth.get()).isEqualTo("Bearer test-fixture");assertThat(version.get()).isEqualTo("v2");
        assertThat(json.readTree(sent.get()).at("/data/slices/0/origin").asText()).isEqualTo("AAA");
        assertThat(json.readTree(sent.get()).at("/data/passengers/0/type").asText()).isEqualTo("adult");
    }
    @Test void roundTripRequestContainsReverseSliceAndChildAges() throws Exception {
        var c=new SearchCriteria("AAA","AAB",LocalDate.of(2026,10,1),LocalDate.of(2026,10,10),3,1,List.of(8),Cabin.PREMIUM_ECONOMY,null);
        new DuffelSearchProvider(http(),new DuffelOfferReader(json,clock),json).search(new FlightSearchProvider.Query(c,c.variants().get(2),clock.instant().plusSeconds(20)));
        var data=json.readTree(sent.get()).path("data");
        assertThat(data.path("slices")).hasSize(2);
        assertThat(data.at("/slices/1/origin").asText()).isEqualTo("AAB");
        assertThat(data.at("/slices/1/departure_date").asText()).isEqualTo("2026-10-11");
        assertThat(data.at("/passengers/1/age").asInt()).isEqualTo(8);
        assertThat(data.path("cabin_class").asText()).isEqualTo("premium_economy");
    }
    @Test void missingBaggageNeverMeansIncluded() throws Exception {
        var root=fixture();segment(root).remove("passengers");
        assertThat(parse(root).offers().get(0).checkedBag()).isEqualTo(BagAvailability.UNKNOWN);
        root=fixture();((ObjectNode)root.at("/data/offers/0/slices/0/segments/0/passengers/0/baggages/0")).put("quantity",0);
        assertThat(parse(root).offers().get(0).checkedBag()).isEqualTo(BagAvailability.NOT_INCLUDED);
    }
    @ParameterizedTest @ValueSource(strings={"live","partial","amount","hugeAmount","currency","expired","owner","timezone","gap","overlap","missing","city"})
    void discardsMalformedOffersWithWarning(String kind) throws Exception {
        var root=fixture();var offer=raw(root);var segment=segment(root);
        switch(kind){
            case "live"->offer.put("live_mode",true);case "partial"->offer.put("partial",true);
            case "amount"->offer.put("total_amount","-1");case "currency"->offer.put("total_currency","XXX_INVALID");
            case "hugeAmount"->offer.put("total_amount","1e+2147483647");
            case "expired"->offer.put("expires_at",clock.instant().minusSeconds(1).toString());
            case "owner"->offer.remove("owner");case "timezone"->((ObjectNode)segment.path("origin")).put("time_zone","invalid");
            case "gap","overlap"->{((ObjectNode)segment.path("origin")).put("time_zone","America/New_York");segment.put("departing_at",kind.equals("gap")?"2026-03-08T02:30:00":"2026-11-01T01:30:00");}
            case "missing"->segment.remove("arriving_at");case "city"->((ObjectNode)segment.path("origin")).put("type","city");
        }
        assertThat(parse(root).offers()).isEmpty();assertThat(parse(root).warnings()).isNotEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"null","{}","{","{\"data\":{\"live_mode\":true,\"offers\":[]}}","{\"data\":{\"offers\":[]}}"})
    void rejectsInvalidOrLiveEnvelope(String body){
        assertThatThrownBy(()->new DuffelOfferReader(json,clock).read(body,query())).isInstanceOf(SearchProviderFailure.class);
    }
    @Test void retries503OnlyWithinAttemptBudget(){failFirst=true;assertThat(http().post("{}",clock.instant().plusSeconds(10))).contains("offers");assertThat(calls).hasValue(2);}
    @ParameterizedTest @ValueSource(ints={400,401,403,422,500,502})
    void doesNotBlindlyRetryPermanentOrUncertainErrors(int code){
        status=code;assertThatThrownBy(()->http().post("{}",clock.instant().plusSeconds(10))).isInstanceOf(SearchProviderFailure.class);
        assertThat(calls).hasValue(1);
    }
    @Test void honorsRateCooldownWithoutRetryStorm(){
        status=429;retryAfter="60";var http=http();
        assertThatThrownBy(()->http.post("{}",clock.instant().plusSeconds(10))).isInstanceOf(SearchProviderFailure.class);
        assertThatThrownBy(()->http.post("{}",clock.instant().plusSeconds(10))).isInstanceOf(SearchProviderFailure.class);
        assertThat(calls).hasValue(1);
    }
    @Test void quotaAndCircuitPreventFurtherNetworkCalls(){
        settings.setRequestsPerWindow(1);var limited=http();limited.post("{}",clock.instant().plusSeconds(10));
        assertThatThrownBy(()->limited.post("{}",clock.instant().plusSeconds(10))).isInstanceOf(SearchProviderFailure.class);assertThat(calls).hasValue(1);
        settings.setRequestsPerWindow(30);settings.setCircuitThreshold(1);status=500;var broken=http();
        assertThatThrownBy(()->broken.post("{}",clock.instant().plusSeconds(10))).isInstanceOf(SearchProviderFailure.class);
        assertThatThrownBy(()->broken.post("{}",clock.instant().plusSeconds(10))).isInstanceOf(SearchProviderFailure.class);assertThat(calls).hasValue(2);
    }
    @Test void boundsResponseBodyAndDoesNotRetryOversizedData(){
        response.set("x".repeat(2048));settings.setMaxResponseBytes(1024);
        assertThatThrownBy(()->http().post("{}",clock.instant().plusSeconds(10))).isInstanceOf(SearchProviderFailure.class);
        assertThat(calls).hasValue(1);
    }
    @Test void boundsSlowResponseAndExpiredDeadline(){
        settings.setMaxAttempts(1);settings.setRequestTimeoutMs(100);delay=500;
        assertThatThrownBy(()->http().post("{}",clock.instant().plusSeconds(10))).isInstanceOf(SearchProviderFailure.class);
        var before=calls.get();assertThatThrownBy(()->http().post("{}",clock.instant().minusSeconds(1))).isInstanceOf(SearchProviderFailure.class);
        assertThat(calls).hasValue(before);
    }
}
