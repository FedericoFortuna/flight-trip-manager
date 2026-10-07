package com.flighttripmanager.flightsearch.infrastructure.persistence.repository;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.flighttripmanager.flightsearch.infrastructure.persistence.entity.SavedOptionJpaEntity;
public interface SavedOptionJpaRepository extends JpaRepository<SavedOptionJpaEntity,UUID> {
    interface Reference { UUID getTripId(); UUID getLegId(); }
    @Query("select o.tripId as tripId,o.legId as legId from SavedOptionJpaEntity o where o.id=:id")
    Optional<Reference> reference(@Param("id") UUID id);
    List<SavedOptionJpaEntity> findByTripIdAndLegIdOrderBySlot(UUID tripId,UUID legId);
    @Modifying(flushAutomatically=true)
    @Query("update SavedOptionJpaEntity o set o.active=false,o.inactiveReason=:reason,o.updatedAt=:at where o.tripId=:tripId and o.legId=:legId and o.active=true")
    void close(@Param("tripId") UUID tripId,@Param("legId") UUID legId,@Param("reason") String reason,@Param("at") Instant at);
}
