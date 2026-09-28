package com.flighttripmanager.trips.infrastructure.persistence.adapter;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.application.port.out.PassengerStore;
import com.flighttripmanager.trips.domain.model.Passenger;
import com.flighttripmanager.trips.infrastructure.persistence.mapper.PassengerPersistenceMapper;
import com.flighttripmanager.trips.infrastructure.persistence.repository.PassengerJpaRepository;

@Component
public class JpaPassengerAdapter implements PassengerStore {
    private final PassengerJpaRepository repository;
    private final PassengerPersistenceMapper mapper;
    public JpaPassengerAdapter(PassengerJpaRepository repository, PassengerPersistenceMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }
    @Override public Optional<Passenger> find(UUID tripId, UUID id) {
        return repository.findByTripIdAndId(tripId, id).map(mapper::toDomain);
    }
    @Override public TripsPage<Passenger> list(UUID tripId, TripsQuery query) {
        var page = repository.findByTripId(tripId, PageRequest.of(query.page(), query.size(),
                Sort.by("createdAt", "id")));
        return new TripsPage<>(page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
    @Override public boolean exists(UUID tripId) { return repository.existsByTripId(tripId); }
    @Override public boolean containsAll(UUID tripId, List<UUID> ids) {
        return ids.isEmpty() || repository.countByTripIdAndIdIn(tripId, ids) == new HashSet<>(ids).size();
    }
    @Override public void save(Passenger passenger) {
        try { repository.saveAndFlush(mapper.toEntity(passenger)); }
        catch (DataIntegrityViolationException error) { throw conflict(); }
    }
    @Override public void delete(UUID tripId, UUID id) {
        try {
            repository.findByTripIdAndId(tripId, id).ifPresent(repository::delete);
            repository.flush();
        } catch (DataIntegrityViolationException error) { throw conflict(); }
    }
    private static TripsException conflict() {
        return new TripsException(TripsException.Reason.CONFLICT, "passenger");
    }
}
