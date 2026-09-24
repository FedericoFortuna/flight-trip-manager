package com.flighttripmanager.trips.application.usecase;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.application.port.in.TripManagement;
import com.flighttripmanager.trips.application.port.out.*;
import com.flighttripmanager.trips.application.mapper.TripMapper;
import com.flighttripmanager.trips.domain.model.*;
import static com.flighttripmanager.trips.application.contract.TripsException.Reason.*;

@Service
// A read assembles the parent and its legs from multiple queries; keep one consistent snapshot.
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class TripService implements TripManagement {
    private final TripStore store;
    private final CatalogPlaces places;
    private final TripMapper mapper;
    private final Clock clock;
    public TripService(TripStore store, CatalogPlaces places, TripMapper mapper, Clock clock) {
        this.store = store; this.places = places; this.mapper = mapper; this.clock = clock;
    }
    @Override @Transactional
    public TripView create(CreateTrip command) {
        return valid(() -> {
            Instant now = now();
            Trip trip = new Trip(UUID.randomUUID(), command.name(), command.startDate(), command.endDate(),
                    command.totalBudgetUsd(), mapper.tripStatus(command.manualStatusOverride()), 0, now, now, List.of());
            store.save(trip);
            return mapper.view(trip, now);
        });
    }
    @Override public TripView get(UUID id) { return mapper.view(require(id, false), now()); }
    @Override public TripsPage<TripView> list(TripsQuery query) {
        var page = store.list(query);
        Instant now = now();
        return new TripsPage<>(page.items().stream().map(trip -> mapper.view(trip, now)).toList(),
                page.page(), page.size(), page.totalElements());
    }
    @Override @Transactional
    public TripView patch(UUID id, PatchTrip patch) {
        return valid(() -> {
            Trip trip = edit(id, patch.version());
            Instant now = modifiedAt(trip);
            Trip changed = trip.changed(patch.name().apply(trip.name()), patch.startDate().apply(trip.startDate()),
                    patch.endDate().apply(trip.endDate()), patch.totalBudgetUsd().apply(trip.totalBudgetUsd()),
                    mapper.tripStatus(patch.manualStatusOverride().apply(mapper.tripStatus(trip.manualStatusOverride()))),
                    trip.legs(), now);
            store.save(changed);
            return mapper.view(changed, now());
        });
    }
    @Override @Transactional
    public void delete(UUID id, long version) {
        Trip trip = edit(id, version);
        if (!trip.legs().isEmpty()) throw new TripsException(TRIP_NOT_EMPTY, "legs");
        store.delete(id);
    }
    @Override @Transactional
    public LegView addLeg(UUID tripId, CreateLeg command) {
        return valid(() -> {
            Trip trip = edit(tripId, command.version());
            Instant now = modifiedAt(trip);
            Integer order = trip.legs().isEmpty() || trip.legs().get(0).manualOrder() == null ? null : trip.legs().size();
            CreateLeg input = new CreateLeg(command.version(), command.origin(), command.destination(), command.transportType(),
                    command.departureDate(), command.departureDateTime(), command.arrivalDate(), command.arrivalDateTime(),
                    command.status() == null ? LegStatusValue.PLANNED : command.status());
            TripLeg leg = mapper.leg(input, UUID.randomUUID(), tripId, order, now);
            places.requireUsable(leg.origin()); places.requireUsable(leg.destination());
            List<TripLeg> legs = new ArrayList<>(trip.legs()); legs.add(leg);
            Trip changed = trip.withLegs(legs, now);
            store.save(changed);
            return mapper.view(leg, changed.version(), now());
        });
    }
    @Override @Transactional
    public LegView patchLeg(UUID tripId, UUID legId, PatchLeg patch) {
        return valid(() -> {
            Trip trip = edit(tripId, patch.version());
            TripLeg old = leg(trip, legId);
            Instant now = modifiedAt(trip);
            LocalDate departure = patch.departureDate().present() ? patch.departureDate().value()
                    : patch.departureDateTime().present() && patch.departureDateTime().value() != null ? null : old.departureDate();
            LocalDate arrival = patch.arrivalDate().present() ? patch.arrivalDate().value()
                    : patch.arrivalDateTime().present() && patch.arrivalDateTime().value() != null ? null : old.arrivalDate();
            TripLeg changedLeg = new TripLeg(old.id(), tripId,
                    mapper.place(patch.origin().apply(mapper.place(old.origin()))),
                    mapper.place(patch.destination().apply(mapper.place(old.destination()))),
                    mapper.transport(patch.transportType().apply(mapper.transport(old.transportType()))),
                    departure, patch.departureDateTime().apply(old.departureDateTime()),
                    arrival, patch.arrivalDateTime().apply(old.arrivalDateTime()),
                    mapper.legStatus(patch.status().apply(mapper.legStatus(old.declaredStatus()))),
                    old.manualOrder(), old.createdAt(), now);
            if (!changedLeg.origin().equals(old.origin())) places.requireUsable(changedLeg.origin());
            if (!changedLeg.destination().equals(old.destination())) places.requireUsable(changedLeg.destination());
            Trip changed = trip.withLegs(trip.legs().stream().map(item -> item.id().equals(legId) ? changedLeg : item).toList(), now);
            store.save(changed);
            return mapper.view(changedLeg, changed.version(), now());
        });
    }
    @Override @Transactional
    public void deleteLeg(UUID tripId, UUID legId, long version) {
        valid(() -> {
            Trip trip = edit(tripId, version);
            leg(trip, legId);
            Instant now = modifiedAt(trip);
            List<TripLeg> remaining = trip.orderedLegs().stream().filter(item -> !item.id().equals(legId)).toList();
            List<TripLeg> normalized = new ArrayList<>();
            for (int i = 0; i < remaining.size(); i++) {
                TripLeg item = remaining.get(i);
                normalized.add(item.manualOrder() == null ? item : item.ordered(i, now));
            }
            store.save(trip.withLegs(normalized, now));
            return null;
        });
    }
    @Override @Transactional
    public TripView reorder(UUID tripId, ReorderLegs command) {
        return valid(() -> {
            Trip trip = edit(tripId, command.version());
            List<UUID> ids = command.orderedLegIds();
            if (ids == null || ids.size() > 500 || ids.stream().anyMatch(Objects::isNull)) throw new TripRuleViolation("orderedLegIds");
            Set<UUID> expected = new HashSet<>(trip.legs().stream().map(TripLeg::id).toList());
            if (!ids.isEmpty() && (ids.size() != expected.size() || !new HashSet<>(ids).equals(expected))) {
                throw new TripRuleViolation("orderedLegIds");
            }
            Instant now = modifiedAt(trip);
            List<TripLeg> ordered = trip.legs().stream().map(item -> item.ordered(ids.isEmpty() ? null : ids.indexOf(item.id()), now)).toList();
            Trip changed = trip.withLegs(ordered, now);
            store.save(changed);
            return mapper.view(changed, now());
        });
    }
    @Override public TripsPage<LegView> legs(UUID tripId, TripsQuery query) {
        Trip trip = require(tripId, false);
        Instant now = now();
        List<LegView> page = trip.orderedLegs().stream().skip((long) query.page() * query.size()).limit(query.size())
                .map(leg -> mapper.view(leg, trip.version(), now)).toList();
        return new TripsPage<>(page, query.page(), query.size(), trip.legs().size());
    }
    private Trip require(UUID id, boolean lock) {
        return store.find(id, lock).orElseThrow(() -> new TripsException(TRIP_NOT_FOUND, "tripId"));
    }
    private Trip edit(UUID id, Long version) {
        if (version == null || version < 0) throw new TripsException(INVALID_REQUEST, "version");
        Trip trip = require(id, true);
        if (trip.version() != version) throw new TripsException(VERSION_CONFLICT, "version");
        return trip;
    }
    private static TripLeg leg(Trip trip, UUID legId) {
        return trip.legs().stream().filter(item -> item.id().equals(legId)).findFirst()
                .orElseThrow(() -> new TripsException(LEG_NOT_FOUND, "legId"));
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private Instant modifiedAt(Trip trip) {
        Instant now = now(); return now.isBefore(trip.updatedAt()) ? trip.updatedAt() : now;
    }
    private static <T> T valid(Supplier<T> action) {
        try { return action.get(); }
        catch (TripRuleViolation error) { throw new TripsException(INVALID_REQUEST, error.field()); }
    }
}
