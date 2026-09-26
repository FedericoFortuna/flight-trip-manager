package com.flighttripmanager.trips.application.usecase;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.application.mapper.PassengerMapper;
import com.flighttripmanager.trips.application.port.in.PassengerManagement;
import com.flighttripmanager.trips.application.port.out.*;
import com.flighttripmanager.trips.domain.model.*;
import static com.flighttripmanager.trips.application.contract.TripsException.Reason.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PassengerService implements PassengerManagement {
    private final TripStore trips;
    private final PassengerStore passengers;
    private final PassengerMapper mapper;
    private final Clock clock;

    public PassengerService(TripStore trips, PassengerStore passengers, PassengerMapper mapper, Clock clock) {
        this.trips = trips; this.passengers = passengers; this.mapper = mapper; this.clock = clock;
    }
    @Override @Transactional
    public PassengerView create(UUID tripId, CreatePassenger command) {
        return valid(() -> {
            Trip trip = edit(tripId, command.version());
            Instant now = modifiedAt(trip);
            Passenger passenger = new Passenger(UUID.randomUUID(), tripId, command.firstName(),
                    command.lastName(), command.notes(), now, now);
            passengers.save(passenger);
            return mapper.view(passenger, advance(trip, now));
        });
    }
    @Override public TripsPage<PassengerView> list(UUID tripId, TripsQuery query) {
        Trip trip = require(tripId, false);
        var page = passengers.list(tripId, query);
        return new TripsPage<>(page.items().stream().map(item -> mapper.view(item, trip.version())).toList(),
                page.page(), page.size(), page.totalElements());
    }
    @Override @Transactional
    public PassengerView patch(UUID tripId, UUID passengerId, PatchPassenger command) {
        return valid(() -> {
            Trip trip = edit(tripId, command.version());
            Passenger old = passenger(tripId, passengerId);
            if (!command.firstName().present() && !command.lastName().present() && !command.notes().present()) {
                throw new TripsException(INVALID_REQUEST, "request");
            }
            Instant now = modifiedAt(trip);
            Passenger changed = new Passenger(old.id(), tripId, command.firstName().apply(old.firstName()),
                    command.lastName().apply(old.lastName()), command.notes().apply(old.notes()), old.createdAt(), now);
            passengers.save(changed);
            return mapper.view(changed, advance(trip, now));
        });
    }
    @Override @Transactional
    public void delete(UUID tripId, UUID passengerId, long version) {
        Trip trip = edit(tripId, version);
        passenger(tripId, passengerId);
        passengers.delete(tripId, passengerId);
        advance(trip, modifiedAt(trip));
    }
    private Passenger passenger(UUID tripId, UUID id) {
        return passengers.find(tripId, id).orElseThrow(() -> new TripsException(PASSENGER_NOT_FOUND, "passengerId"));
    }
    private Trip require(UUID id, boolean lock) {
        return trips.find(id, lock).orElseThrow(() -> new TripsException(TRIP_NOT_FOUND, "tripId"));
    }
    private Trip edit(UUID id, Long version) {
        if (version == null || version < 0) throw new TripsException(INVALID_REQUEST, "version");
        Trip trip = require(id, true);
        if (trip.version() != version) throw new TripsException(VERSION_CONFLICT, "version");
        return trip;
    }
    private Instant modifiedAt(Trip trip) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return now.isBefore(trip.updatedAt()) ? trip.updatedAt() : now;
    }
    private long advance(Trip trip, Instant now) {
        Trip changed = trip.withLegs(trip.legs(), now);
        trips.saveHeader(changed);
        return changed.version();
    }
    private static <T> T valid(Supplier<T> action) {
        try { return action.get(); }
        catch (TripRuleViolation error) { throw new TripsException(INVALID_REQUEST, error.field()); }
    }
}
