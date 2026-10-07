package com.flighttripmanager.flightsearch.infrastructure.providers;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.flighttripmanager.flightsearch.application.port.out.*;
import com.flighttripmanager.flightsearch.domain.model.*;
public class MockSearchProvider implements FlightSearchProvider {
    private final SearchAirports airports;private final Clock clock;
    public MockSearchProvider(SearchAirports airports,Clock clock){this.airports=airports;this.clock=clock;}
    @Override public Batch search(Query query){
        List<SearchOffer> offers=new ArrayList<>();var c=query.criteria();Instant now=clock.instant();
        for(int variant=0;variant<3;variant++){
            List<SearchSlice> slices=new ArrayList<>();
            slices.add(slice(c.origin(),c.destination(),query.dates().departure(),variant));
            if(query.dates().returning()!=null)slices.add(slice(c.destination(),c.origin(),query.dates().returning(),variant));
            BigDecimal amount=BigDecimal.valueOf((250+variant*65+Math.abs(query.dates().offset())*9L)*(c.adults()+c.childAges().size())*(slices.size()));
            offers.add(new SearchOffer("MOCK","mock-"+query.dates().offset()+"-"+variant,"ZZ",true,true,amount,"USD",
                variant==0?BagAvailability.UNKNOWN:BagAvailability.INCLUDED,now,now.plusSeconds(180),slices));
        }
        return new Batch(offers,List.of());
    }
    private SearchSlice slice(String origin,String destination,LocalDate date,int variant){
        var from=airports.requireActive(origin);var to=airports.requireActive(destination);
        LocalDateTime local=date.atTime(9+variant*3,0);Instant departure=local.atZone(from.timezone()).toInstant();
        Instant arrival=departure.plus(Duration.ofHours(4+variant));
        return new SearchSlice(List.of(new SearchSegment(origin,destination,"ZZ","ZZ","ZZ"+(100+variant),local,
            LocalDateTime.ofInstant(arrival,to.timezone()),departure,arrival,List.of())));
    }
}
