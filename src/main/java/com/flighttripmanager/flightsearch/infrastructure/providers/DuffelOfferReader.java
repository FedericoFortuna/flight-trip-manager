package com.flighttripmanager.flightsearch.infrastructure.providers;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.flighttripmanager.flightsearch.application.port.out.*;
import com.flighttripmanager.flightsearch.domain.model.*;
public final class DuffelOfferReader {
    private final ObjectMapper json;private final Clock clock;
    public DuffelOfferReader(ObjectMapper json,Clock clock){this.json=json;this.clock=clock;}
    public FlightSearchProvider.Batch read(String body,FlightSearchProvider.Query query){
        JsonNode data;
        try{data=json.readTree(body).path("data");}catch(JsonProcessingException|NullPointerException e){throw failure();}
        // This card is test-only. Never present live results as sandbox results.
        if(!data.path("live_mode").isBoolean()||data.path("live_mode").asBoolean()||!data.path("offers").isArray())throw failure();
        List<SearchOffer> offers=new ArrayList<>();Set<String> warnings=new LinkedHashSet<>();
        int count=0;
        for(JsonNode raw:data.path("offers")){
            if(++count>500){warnings.add("PROVIDER_RESULT_LIMIT");break;}
            try{SearchOffer offer=offer(raw,query);if(offer.expiresAt().isAfter(clock.instant()))offers.add(offer);else warnings.add("EXPIRED_PROVIDER_OFFER");}
            catch(SearchRuleViolation|IllegalArgumentException|DateTimeException e){warnings.add("INVALID_PROVIDER_OFFER_REMOVED");}
        }
        return new FlightSearchProvider.Batch(offers,List.copyOf(warnings));
    }
    private SearchOffer offer(JsonNode raw,FlightSearchProvider.Query query){
        if(!raw.path("live_mode").isBoolean()||raw.path("live_mode").asBoolean()||raw.path("partial").asBoolean(false))throw bad();
        String owner=text(raw.path("owner"),"iata_code");
        List<SearchSlice> slices=new ArrayList<>();List<BagAvailability> bags=new ArrayList<>();
        for(JsonNode slice:array(raw,"slices")){
            List<SearchSegment> segments=new ArrayList<>();
            for(JsonNode segment:array(slice,"segments")){
                var origin=segment.path("origin");var destination=segment.path("destination");
                if(!"airport".equals(text(origin,"type"))||!"airport".equals(text(destination,"type")))throw bad();
                LocalDateTime departure=LocalDateTime.parse(text(segment,"departing_at"));
                LocalDateTime arrival=LocalDateTime.parse(text(segment,"arriving_at"));
                Instant departureInstant=instant(departure,text(origin,"time_zone"));
                Instant arrivalInstant=instant(arrival,text(destination,"time_zone"));
                List<String> technical=new ArrayList<>();
                if(segment.hasNonNull("stops"))for(JsonNode stop:array(segment,"stops"))technical.add(text(stop.path("airport"),"iata_code"));
                String marketing=text(segment.path("marketing_carrier"),"iata_code");
                String operating=segment.path("operating_carrier").path("iata_code").isTextual()?segment.path("operating_carrier").path("iata_code").asText():null;
                segments.add(new SearchSegment(text(origin,"iata_code"),text(destination,"iata_code"),marketing,operating,
                    marketing+text(segment,"marketing_carrier_flight_number"),departure,arrival,departureInstant,arrivalInstant,technical));
                bags.add(bags(segment,query.criteria().adults()+query.criteria().childAges().size()));
            }
            slices.add(new SearchSlice(segments));
        }
        BagAvailability baggage=bags.stream().allMatch(b->b==BagAvailability.INCLUDED)?BagAvailability.INCLUDED:
            bags.contains(BagAvailability.NOT_INCLUDED)?BagAvailability.NOT_INCLUDED:BagAvailability.UNKNOWN;
        return new SearchOffer("DUFFEL",text(raw,"id"),owner,false,true,new BigDecimal(text(raw,"total_amount")),
            text(raw,"total_currency"),baggage,clock.instant(),Instant.parse(text(raw,"expires_at")),slices);
    }
    private BagAvailability bags(JsonNode segment,int expected){
        JsonNode passengers=segment.path("passengers");
        if(!passengers.isArray()||passengers.size()!=expected)return BagAvailability.UNKNOWN;
        boolean missing=false,none=false;
        for(JsonNode passenger:passengers){
            JsonNode bags=passenger.path("baggages");boolean found=false,included=false;
            if(bags.isArray())for(JsonNode bag:bags){
                if("checked".equals(bag.path("type").asText())&&bag.path("quantity").isIntegralNumber()){
                    found=true;included|=bag.path("quantity").asInt()>0;
                }
            }
            if(!found)missing=true;else if(!included)none=true;
        }
        return none?BagAvailability.NOT_INCLUDED:missing?BagAvailability.UNKNOWN:BagAvailability.INCLUDED;
    }
    private Instant instant(LocalDateTime local,String zone){
        var offsets=ZoneId.of(zone).getRules().getValidOffsets(local);
        if(offsets.size()!=1)throw bad(); // Ambiguous or nonexistent local times are not guessed.
        return local.toInstant(offsets.get(0));
    }
    private Iterable<JsonNode> array(JsonNode node,String field){JsonNode value=node.path(field);if(!value.isArray())throw bad();return value;}
    private String text(JsonNode node,String field){JsonNode value=node.path(field);if(!value.isTextual()||value.asText().isBlank())throw bad();return value.asText();}
    private IllegalArgumentException bad(){return new IllegalArgumentException("Invalid provider data");}
    private SearchProviderFailure failure(){return new SearchProviderFailure(SearchProviderFailure.Reason.INVALID_RESPONSE);}
}
