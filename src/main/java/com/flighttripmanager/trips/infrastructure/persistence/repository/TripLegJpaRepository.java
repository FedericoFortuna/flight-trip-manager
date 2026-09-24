package com.flighttripmanager.trips.infrastructure.persistence.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.flighttripmanager.trips.infrastructure.persistence.entity.TripLegJpaEntity;

public interface TripLegJpaRepository extends JpaRepository<TripLegJpaEntity, UUID> {
    List<TripLegJpaEntity> findByTripId(UUID tripId);
    List<TripLegJpaEntity> findByTripIdIn(Collection<UUID> tripIds);
}
