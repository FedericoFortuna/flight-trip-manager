package com.flighttripmanager.trips.application.port.in;
import java.util.*;
import com.flighttripmanager.trips.application.contract.FlightTripContext;
public interface TripFlightAccess {
    FlightTripContext inspect(UUID tripId, UUID legId);
    FlightTripContext lock(UUID tripId, UUID legId, List<UUID> passengerIds);
    long advance(UUID tripId, long version);
}
