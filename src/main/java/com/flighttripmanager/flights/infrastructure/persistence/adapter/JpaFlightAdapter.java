package com.flighttripmanager.flights.infrastructure.persistence.adapter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import com.flighttripmanager.flights.application.contract.*;
import com.flighttripmanager.flights.application.port.out.FlightStore;
import com.flighttripmanager.flights.domain.model.*;
import com.flighttripmanager.flights.infrastructure.persistence.entity.*;
import com.flighttripmanager.flights.infrastructure.persistence.mapper.FlightPersistenceMapper;
import com.flighttripmanager.flights.infrastructure.persistence.repository.*;
@Component
public class JpaFlightAdapter implements FlightStore {
    private final jakarta.persistence.EntityManager entityManager;
    private final FlightJpaRepository flights;
    private final FlightPassengerJpaRepository passengers;
    private final FlightHistoryJpaRepository history;
    private final FlightPersistenceMapper mapper;
    public JpaFlightAdapter(FlightJpaRepository flights, FlightPassengerJpaRepository passengers,
            FlightHistoryJpaRepository history, FlightPersistenceMapper mapper, jakarta.persistence.EntityManager entityManager) {
        this.flights=flights; this.passengers=passengers; this.history=history; this.mapper=mapper;
        this.entityManager=entityManager;
    }
    @Override public Optional<Reference> reference(UUID id) {
        return flights.reference(id).map(item -> new Reference(item.getTripId(), item.getTripLegId()));
    }
    @Override public Optional<FlightSegment> find(UUID id) {
        return flights.findById(id).map(item -> mapper.domain(item, passengers.findByFlightId(id)));
    }
    @Override public FlightPage<FlightSegment> list(UUID tripId, UUID legId, FlightQuery query) {
        var page = flights.findByTripIdAndTripLegId(tripId, legId,
                PageRequest.of(query.page(), query.size(), Sort.by("flightDate", "createdAt", "id")));
        var ids = page.getContent().stream().map(FlightJpaEntity::getId).toList();
        Map<UUID,List<FlightPassengerJpaEntity>> grouped = ids.isEmpty() ? Map.of()
                : passengers.findByFlightIdIn(ids).stream().collect(Collectors.groupingBy(FlightPassengerJpaEntity::getFlightId));
        return new FlightPage<>(page.getContent().stream().map(item -> mapper.domain(item, grouped.getOrDefault(item.getId(), List.of()))).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
    @Override public void save(FlightSegment flight, List<FlightHistory> changes) {
        write(flight, changes, false);
    }
    @Override public void insert(FlightSegment flight, List<FlightHistory> changes) {
        write(flight, changes, true);
    }
    private void write(FlightSegment flight, List<FlightHistory> changes, boolean insert) {
        try {
            // Assigned idempotency UUIDs must INSERT, never merge into a concurrent registration.
            if (insert) {
                entityManager.persist(mapper.entity(flight));
                entityManager.flush();
            } else {
                flights.saveAndFlush(mapper.entity(flight));
            }
            passengers.deleteByFlightId(flight.id()); passengers.flush();
            passengers.saveAllAndFlush(flight.passengers().stream().map(item -> mapper.entity(item, UUID.randomUUID(), flight.id(), flight.tripId())).toList());
            history.saveAllAndFlush(changes.stream().map(mapper::entity).toList());
        } catch (DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException error) { throw conflict(); }
    }
    @Override public void delete(UUID id) {
        try {
            passengers.deleteByFlightId(id); history.deleteByFlightId(id);
            passengers.flush(); history.flush();
            flights.deleteById(id); flights.flush();
        } catch (DataIntegrityViolationException error) { throw conflict(); }
    }
    @Override public FlightPage<FlightHistory> history(UUID id, FlightQuery query) {
        var page = history.findByFlightId(id, PageRequest.of(query.page(), query.size(), Sort.by("revision", "field", "id")));
        return new FlightPage<>(page.getContent().stream().map(mapper::domain).toList(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
    private FlightException conflict() { return new FlightException(FlightException.Reason.CONFLICT, "flight"); }
}
