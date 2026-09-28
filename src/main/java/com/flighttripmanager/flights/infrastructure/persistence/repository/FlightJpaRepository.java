package com.flighttripmanager.flights.infrastructure.persistence.repository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.flighttripmanager.flights.infrastructure.persistence.entity.*;

public interface FlightJpaRepository extends JpaRepository<FlightJpaEntity, UUID> {
    interface ParentReference { UUID getTripId(); UUID getTripLegId(); }
    @Query("select f.tripId as tripId, f.tripLegId as tripLegId from FlightJpaEntity f where f.id=:id")
    Optional<ParentReference> reference(@Param("id") UUID id);
    Page<FlightJpaEntity> findByTripIdAndTripLegId(UUID tripId, UUID tripLegId, Pageable pageable);
}
