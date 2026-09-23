package com.flighttripmanager.catalog.infrastructure.persistence.repository;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import com.flighttripmanager.catalog.infrastructure.persistence.entity.AirlineJpaEntity;

public interface AirlineJpaRepository extends Repository<AirlineJpaEntity, UUID> {
    Optional<AirlineJpaEntity> findByIataCode(String iataCode);

    @Query("""
            select e from AirlineJpaEntity e
            where e.active = :active
              and (:q = '' or locate(:q, lower(e.iataCode)) > 0
                   or locate(:q, lower(e.icaoCode)) > 0
                   or locate(:q, lower(e.name)) > 0
                   or locate(:q, lower(e.country)) > 0)
            """)
    Page<AirlineJpaEntity> search(@Param("q") String q, @Param("active") boolean active, Pageable pageable);
}
