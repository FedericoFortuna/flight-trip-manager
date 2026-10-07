package com.flighttripmanager.flightsearch.infrastructure.catalog;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import com.flighttripmanager.catalog.application.port.in.CatalogLookup;
import com.flighttripmanager.catalog.application.contract.CatalogNotFoundException;
import com.flighttripmanager.flightsearch.application.port.out.SearchAirports;
import com.flighttripmanager.flightsearch.application.contract.SearchException;
@Component
public class SearchAirportsAdapter implements SearchAirports {
    private final CatalogLookup catalog;
    public SearchAirportsAdapter(CatalogLookup catalog){this.catalog=catalog;}
    @Override public Airport requireActive(String iata){
        try{var value=catalog.airport(iata);if(!value.active())throw invalid();return new Airport(value.iataCode(),ZoneId.of(value.timezone()));}
        catch(CatalogNotFoundException e){throw invalid();}
    }
    private SearchException invalid(){return new SearchException(SearchException.Reason.INVALID_REFERENCE,"airport");}
}
