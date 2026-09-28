package com.flighttripmanager.flights.application.usecase;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.flighttripmanager.flights.application.contract.*;
import com.flighttripmanager.flights.application.mapper.FlightMapper;
import com.flighttripmanager.flights.application.port.in.FlightManagement;
import com.flighttripmanager.flights.application.port.out.*;
import com.flighttripmanager.flights.domain.model.*;
import static com.flighttripmanager.flights.application.contract.FlightException.Reason.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FlightService implements FlightManagement {
    private final FlightStore store;
    private final FlightTrips trips;
    private final FlightCatalog catalog;
    private final FlightMapper mapper;
    private final Clock clock;
    public FlightService(FlightStore store, FlightTrips trips, FlightCatalog catalog, FlightMapper mapper, Clock clock) {
        this.store = store; this.trips = trips; this.catalog = catalog; this.mapper = mapper; this.clock = clock;
    }
    @Override @Transactional
    public FlightView create(UUID tripId, UUID legId, CreateFlight command) {
        return valid(() -> {
            version(command.version());
            if (command.data() == null) throw new FlightException(INVALID_REQUEST, "data");
            var context = trips.lock(tripId, legId, List.of());
            UUID id = command.idempotencyKey() == null ? UUID.randomUUID() : command.idempotencyKey();
            Instant now = now(context.updatedAt());
            FlightSegment candidate = domain(command.data(), id, tripId, legId, null, now);
            var existing = store.find(id);
            if (existing.isPresent()) {
                FlightSegment previous = existing.orElseThrow();
                if (!previous.tripId().equals(tripId) || !previous.tripLegId().equals(legId)
                        || !mapper.data(previous).equals(mapper.data(candidate))) throw new FlightException(CONFLICT, "idempotencyKey");
                return mapper.view(previous, context.version());
            }
            checkVersion(command.version(), context.version());
            trips.lock(tripId, legId, ids(candidate));
            catalog.validate(candidate, null);
            long revision = trips.advance(tripId, context.version());
            store.insert(candidate, FlightHistory.changes(null, candidate, revision));
            return mapper.view(candidate, revision);
        });
    }
    @Override public FlightView get(UUID id) {
        FlightSegment flight = require(id);
        return mapper.view(flight, trips.inspect(flight.tripId(), flight.tripLegId()).version());
    }
    @Override public FlightPage<FlightView> list(UUID tripId, UUID legId, FlightQuery query) {
        long version = trips.inspect(tripId, legId).version();
        var page = store.list(tripId, legId, query);
        return new FlightPage<>(page.items().stream().map(item -> mapper.view(item, version)).toList(),
                page.page(), page.size(), page.totalElements());
    }
    @Override @Transactional
    public FlightView patch(UUID id, FlightPatch patch) {
        return valid(() -> {
            version(patch.version());
            var reference = reference(id);
            var context = trips.lock(reference.tripId(), reference.legId(), List.of());
            checkVersion(patch.version(), context.version());
            // Read the entity only after acquiring the parent lock; the pre-lock lookup is a scalar projection.
            FlightSegment old = require(id);
            FlightSegment changed = domain(patch.apply(mapper.data(old)), id, old.tripId(), old.tripLegId(), old, now(context.updatedAt()));
            trips.lock(old.tripId(), old.tripLegId(), ids(changed));
            catalog.validate(changed, old);
            long revision = trips.advance(old.tripId(), context.version());
            store.save(changed, FlightHistory.changes(old, changed, revision));
            return mapper.view(changed, revision);
        });
    }
    @Override @Transactional
    public void delete(UUID id, long version) {
        version(version);
        var reference = reference(id);
        var context = trips.lock(reference.tripId(), reference.legId(), List.of());
        checkVersion(version, context.version());
        require(id);
        store.delete(id);
        trips.advance(reference.tripId(), context.version());
    }
    @Override public FlightPage<FlightHistoryView> history(UUID id, FlightQuery query) {
        require(id);
        var page = store.history(id, query);
        return new FlightPage<>(page.items().stream().map(mapper::view).toList(), page.page(), page.size(), page.totalElements());
    }
    private FlightSegment domain(FlightData data, UUID id, UUID tripId, UUID legId, FlightSegment old, Instant now) {
        Instant departure = data.schedule() == null ? null : data.schedule().scheduledDeparture();
        Instant arrival = data.schedule() == null ? null : data.schedule().scheduledArrival();
        return mapper.domain(data, id, tripId, legId,
                old == null ? departure : old.originalScheduledDeparture(),
                old == null ? arrival : old.originalScheduledArrival(),
                old == null ? now : old.createdAt(), now);
    }
    private FlightSegment require(UUID id) {
        return store.find(id).orElseThrow(() -> new FlightException(NOT_FOUND, "flightId"));
    }
    private FlightStore.Reference reference(UUID id) {
        return store.reference(id).orElseThrow(() -> new FlightException(NOT_FOUND, "flightId"));
    }
    private static List<UUID> ids(FlightSegment flight) { return flight.passengers().stream().map(FlightPassenger::passengerId).toList(); }
    private Instant now(Instant previous) {
        Instant value = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return value.isBefore(previous) ? previous : value;
    }
    private static void version(Long value) { if (value == null || value < 0) throw new FlightException(INVALID_REQUEST, "version"); }
    private static void checkVersion(long expected, long actual) {
        if (expected != actual) throw new FlightException(VERSION_CONFLICT, "version");
    }
    private static <T> T valid(Supplier<T> action) {
        try { return action.get(); }
        catch (FlightRuleViolation error) { throw new FlightException(INVALID_REQUEST, error.field()); }
    }
}
