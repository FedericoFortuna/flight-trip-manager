package com.flighttripmanager.trips.application.port.out;

import java.util.*;
import com.flighttripmanager.trips.domain.model.Trip;
import com.flighttripmanager.trips.application.contract.*;
public interface TripStore {
    Optional<Trip> find(UUID id, boolean forUpdate);
    TripsPage<Trip> list(TripsQuery query);
    void save(Trip trip);
    void delete(UUID id);
}
