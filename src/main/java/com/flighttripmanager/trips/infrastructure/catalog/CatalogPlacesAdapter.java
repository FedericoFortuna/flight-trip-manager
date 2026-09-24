package com.flighttripmanager.trips.infrastructure.catalog;

import org.springframework.stereotype.Component;
import com.flighttripmanager.catalog.application.port.in.CatalogLookup;
import com.flighttripmanager.catalog.application.contract.CatalogNotFoundException;
import com.flighttripmanager.trips.application.port.out.CatalogPlaces;
import com.flighttripmanager.trips.application.contract.TripsException;
import com.flighttripmanager.trips.domain.model.*;

@Component
public class CatalogPlacesAdapter implements CatalogPlaces {
    private final CatalogLookup catalog;
    public CatalogPlacesAdapter(CatalogLookup catalog) { this.catalog = catalog; }
    @Override public void requireUsable(Place place) {
        try {
            if (place.kind() == PlaceType.AIRPORT) {
                if (!catalog.airportById(place.id()).active()) {
                    throw new TripsException(TripsException.Reason.INVALID_REFERENCE, "place");
                }
            } else catalog.location(place.id());
        } catch (CatalogNotFoundException error) {
            throw new TripsException(TripsException.Reason.INVALID_REFERENCE, "place");
        }
    }
}
