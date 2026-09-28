package com.flighttripmanager.flights.infrastructure.trips;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import com.flighttripmanager.trips.application.port.in.TripFlightAccess;
import com.flighttripmanager.flights.application.port.out.FlightTrips;
@Component
public class FlightTripsAdapter implements FlightTrips {
    private final TripFlightAccess trips;
    public FlightTripsAdapter(TripFlightAccess trips) { this.trips = trips; }
    @Override public Context inspect(UUID tripId, UUID legId) {
        var c = trips.inspect(tripId, legId); return new Context(c.version(), c.updatedAt());
    }
    @Override public Context lock(UUID tripId, UUID legId, List<UUID> passengers) {
        var c = trips.lock(tripId, legId, passengers); return new Context(c.version(), c.updatedAt());
    }
    @Override public long advance(UUID tripId, long version) { return trips.advance(tripId, version); }
}
