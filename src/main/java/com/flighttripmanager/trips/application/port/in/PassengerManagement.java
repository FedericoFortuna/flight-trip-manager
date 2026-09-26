package com.flighttripmanager.trips.application.port.in;
import java.util.UUID;
import com.flighttripmanager.trips.application.contract.*;
public interface PassengerManagement {
    PassengerView create(UUID tripId, CreatePassenger command);
    TripsPage<PassengerView> list(UUID tripId, TripsQuery query);
    PassengerView patch(UUID tripId, UUID passengerId, PatchPassenger command);
    void delete(UUID tripId, UUID passengerId, long version);
}
