package com.flighttripmanager.flightsearch.application.port.out;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;

import com.flighttripmanager.flightsearch.domain.model.*;
public interface FlightSearchProvider {
    record Query(SearchCriteria criteria,SearchCriteria.Dates dates,Instant deadline){}
    record Batch(List<SearchOffer> offers,List<String> warnings){public Batch{offers=List.copyOf(offers);warnings=List.copyOf(warnings);}}
    Batch search(Query query);
}
