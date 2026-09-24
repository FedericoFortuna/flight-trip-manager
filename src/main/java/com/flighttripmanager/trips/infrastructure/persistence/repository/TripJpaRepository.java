package com.flighttripmanager.trips.infrastructure.persistence.repository;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import com.flighttripmanager.trips.infrastructure.persistence.entity.TripJpaEntity;

public interface TripJpaRepository extends JpaRepository<TripJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TripJpaEntity t where t.id = :id")
    Optional<TripJpaEntity> findForUpdate(@Param("id") UUID id);

    @Query("select t from TripJpaEntity t where :q = '' or locate(:q, lower(t.name)) > 0")
    Page<TripJpaEntity> search(@Param("q") String q, Pageable pageable);
}
