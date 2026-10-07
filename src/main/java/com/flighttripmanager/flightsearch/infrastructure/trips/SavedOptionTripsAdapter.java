package com.flighttripmanager.flightsearch.infrastructure.trips;
import java.util.*;
import org.springframework.stereotype.Component;
import com.flighttripmanager.flightsearch.application.port.out.SavedOptionTrips;
import com.flighttripmanager.flightsearch.application.contract.SavedOptionException;
import com.flighttripmanager.trips.application.port.in.*;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.catalog.application.port.in.CatalogLookup;
@Component
public class SavedOptionTripsAdapter implements SavedOptionTrips {
    private final TripOptionAccess options;private final TripFlightAccess trips;private final CatalogLookup catalog;
    public SavedOptionTripsAdapter(TripOptionAccess options,TripFlightAccess trips,CatalogLookup catalog){
        this.options=options;this.trips=trips;this.catalog=catalog;
    }
    @Override public Context inspect(UUID tripId,UUID legId,boolean lock){
        var c=options.inspectOptions(tripId,legId,lock);return new Context(c.version(),c.updatedAt(),c.acceptingAlternatives());
    }
    @Override public void validateRoute(UUID tripId,UUID legId,String origin,String destination){
        var c=options.inspectOptions(tripId,legId,false);
        if(c.origin().kind()!=PlaceTypeValue.AIRPORT||c.destination().kind()!=PlaceTypeValue.AIRPORT)
            throw new SavedOptionException(SavedOptionException.Reason.OFFER_MISMATCH);
        var from=catalog.airportById(c.origin().id());var to=catalog.airportById(c.destination().id());
        if(!from.active()||!to.active()||!from.iataCode().equals(origin)||!to.iataCode().equals(destination))
            throw new SavedOptionException(SavedOptionException.Reason.OFFER_MISMATCH);
    }
    @Override public long advance(UUID tripId,long version){return trips.advance(tripId,version);}
}
