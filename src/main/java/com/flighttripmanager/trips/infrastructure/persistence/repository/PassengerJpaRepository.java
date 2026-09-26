package com.flighttripmanager.trips.infrastructure.persistence.repository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.flighttripmanager.trips.infrastructure.persistence.entity.PassengerJpaEntity;
public interface PassengerJpaRepository extends JpaRepository<PassengerJpaEntity, UUID> {
    Optional<PassengerJpaEntity> findByTripIdAndId(UUID tripId, UUID id);
    Page<PassengerJpaEntity> findByTripId(UUID tripId, Pageable pageable);
    boolean existsByTripId(UUID tripId);
}
