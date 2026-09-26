package com.flighttripmanager.trips.application.port.out;
import java.util.*;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.domain.model.Passenger;
public interface PassengerStore {
    Optional<Passenger> find(UUID tripId, UUID passengerId);
    TripsPage<Passenger> list(UUID tripId, TripsQuery query);
    boolean exists(UUID tripId);
    void save(Passenger passenger);
    void delete(UUID tripId, UUID passengerId);
}
