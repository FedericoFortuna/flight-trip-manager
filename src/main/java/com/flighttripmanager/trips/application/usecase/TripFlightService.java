package com.flighttripmanager.trips.application.usecase;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.application.port.in.TripFlightAccess;
import com.flighttripmanager.trips.application.port.out.*;
import com.flighttripmanager.trips.domain.model.*;
import static com.flighttripmanager.trips.application.contract.TripsException.Reason.*;

@Service
public class TripFlightService implements TripFlightAccess {
    private final TripStore trips;
    private final PassengerStore passengers;
    private final Clock clock;
    public TripFlightService(TripStore trips, PassengerStore passengers, Clock clock) {
        this.trips = trips; this.passengers = passengers; this.clock = clock;
    }
    @Override @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public FlightTripContext inspect(UUID tripId, UUID legId) { return context(require(tripId, false), legId); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public FlightTripContext lock(UUID tripId, UUID legId, List<UUID> passengerIds) {
        Trip trip = require(tripId, true);
        var context = context(trip, legId);
        if (!passengers.containsAll(tripId, passengerIds)) throw new TripsException(INVALID_REFERENCE, "passengers");
        return context;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public long advance(UUID tripId, long version) {
        Trip trip = edit(tripId, version);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (now.isBefore(trip.updatedAt())) now = trip.updatedAt();
        Trip updated = trip.withLegs(trip.legs(), now);
        trips.saveHeader(updated);
        return updated.version();
    }
    private Trip require(UUID id, boolean lock) {
        return trips.find(id, lock).orElseThrow(() -> new TripsException(TRIP_NOT_FOUND, "tripId"));
    }
    private Trip edit(UUID id, long version) {
        if (version < 0) throw new TripsException(INVALID_REQUEST, "version");
        Trip trip = require(id, true);
        if (trip.version() != version) throw new TripsException(VERSION_CONFLICT, "version");
        return trip;
    }
    private FlightTripContext context(Trip trip, UUID legId) {
        TripLeg leg = trip.legs().stream().filter(item -> item.id().equals(legId)).findFirst()
                .orElseThrow(() -> new TripsException(LEG_NOT_FOUND, "legId"));
        if (leg.transportType() != TransportType.FLIGHT) throw new TripsException(INVALID_REFERENCE, "transportType");
        return new FlightTripContext(trip.id(), leg.id(), trip.version(), trip.updatedAt());
    }
}
