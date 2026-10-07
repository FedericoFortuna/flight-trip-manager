package com.flighttripmanager.flightsearch.infrastructure.providers;
import java.util.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flighttripmanager.flightsearch.application.port.out.*;
public final class DuffelSearchProvider implements FlightSearchProvider {
    private final DuffelHttp http;private final DuffelOfferReader reader;private final ObjectMapper json;
    public DuffelSearchProvider(DuffelHttp http,DuffelOfferReader reader,ObjectMapper json){this.http=http;this.reader=reader;this.json=json;}
    @Override public Batch search(Query query){
        var c=query.criteria();List<Map<String,Object>> slices=new ArrayList<>();
        slices.add(Map.of("origin",c.origin(),"destination",c.destination(),"departure_date",query.dates().departure().toString()));
        if(query.dates().returning()!=null)slices.add(Map.of("origin",c.destination(),"destination",c.origin(),"departure_date",query.dates().returning().toString()));
        List<Map<String,Object>> passengers=new ArrayList<>();
        for(int i=0;i<c.adults();i++)passengers.add(Map.of("type","adult"));
        for(int age:c.childAges())passengers.add(Map.of("age",age));
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("slices",slices);data.put("passengers",passengers);data.put("cabin_class",c.cabin().name().toLowerCase(Locale.ROOT));
        data.put("max_connections",c.filters().maxStops()==null?3:c.filters().maxStops());
        try{return reader.read(http.post(json.writeValueAsString(Map.of("data",data)),query.deadline()),query);}
        catch(JsonProcessingException e){throw new SearchProviderFailure(SearchProviderFailure.Reason.INVALID_RESPONSE);}
    }
}
