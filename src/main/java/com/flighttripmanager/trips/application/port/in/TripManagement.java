package com.flighttripmanager.trips.application.port.in;

import java.util.UUID;
import com.flighttripmanager.trips.application.contract.*;
public interface TripManagement {
    TripView create(CreateTrip command);
    TripView get(UUID id);
    TripsPage<TripView> list(TripsQuery query);
    TripView patch(UUID id, PatchTrip command);
    void delete(UUID id, long version);
    LegView addLeg(UUID tripId, CreateLeg command);
    LegView patchLeg(UUID tripId, UUID legId, PatchLeg command);
    void deleteLeg(UUID tripId, UUID legId, long version);
    TripView reorder(UUID tripId, ReorderLegs command);
    TripsPage<LegView> legs(UUID tripId, TripsQuery query);
}
