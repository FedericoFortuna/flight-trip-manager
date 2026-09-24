package com.flighttripmanager.trips.infrastructure.persistence.adapter;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.application.port.out.TripStore;
import com.flighttripmanager.trips.domain.model.Trip;
import com.flighttripmanager.trips.infrastructure.persistence.entity.*;
import com.flighttripmanager.trips.infrastructure.persistence.repository.*;
import com.flighttripmanager.trips.infrastructure.persistence.mapper.TripPersistenceMapper;

@Component
public class JpaTripAdapter implements TripStore {
    private final TripJpaRepository trips;
    private final TripLegJpaRepository legs;
    private final TripPersistenceMapper mapper;
    public JpaTripAdapter(TripJpaRepository trips, TripLegJpaRepository legs, TripPersistenceMapper mapper) {
        this.trips = trips; this.legs = legs; this.mapper = mapper;
    }
    @Override public Optional<Trip> find(UUID id, boolean forUpdate) {
        return (forUpdate ? trips.findForUpdate(id) : trips.findById(id))
                .map(entity -> mapper.toDomain(entity, legs.findByTripId(id)));
    }
    @Override public TripsPage<Trip> list(TripsQuery query) {
        var page = trips.search(query.q(), PageRequest.of(query.page(), query.size(), Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        List<UUID> ids = page.getContent().stream().map(TripJpaEntity::getId).toList();
        Map<UUID, List<TripLegJpaEntity>> grouped = ids.isEmpty() ? Map.of()
                : legs.findByTripIdIn(ids).stream().collect(Collectors.groupingBy(TripLegJpaEntity::getTripId));
        return new TripsPage<>(page.getContent().stream()
                .map(entity -> mapper.toDomain(entity, grouped.getOrDefault(entity.getId(), List.of()))).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
    @Override public void save(Trip trip) {
        try {
            trips.saveAndFlush(mapper.toEntity(trip));
            Set<UUID> keep = trip.legs().stream().map(leg -> leg.id()).collect(Collectors.toSet());
            legs.deleteAll(legs.findByTripId(trip.id()).stream().filter(leg -> !keep.contains(leg.getId())).toList());
            legs.saveAllAndFlush(trip.legs().stream().map(mapper::toEntity).toList());
        } catch (DataIntegrityViolationException error) {
            throw new TripsException(TripsException.Reason.CONFLICT, "trip");
        }
    }
    @Override public void delete(UUID id) {
        try { trips.deleteById(id); trips.flush(); }
        catch (DataIntegrityViolationException error) { throw new TripsException(TripsException.Reason.CONFLICT, "trip"); }
    }
}
