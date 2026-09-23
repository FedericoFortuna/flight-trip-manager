package com.flighttripmanager.catalog.infrastructure.persistence.repository;

import java.util.UUID;
import com.flighttripmanager.catalog.domain.model.LocationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import com.flighttripmanager.catalog.infrastructure.persistence.entity.LocationJpaEntity;

public interface LocationJpaRepository extends Repository<LocationJpaEntity, UUID> {
    java.util.Optional<LocationJpaEntity> findById(UUID id);

    @Query("""
            select e from LocationJpaEntity e
            where (:type is null or e.type = :type)
              and (:q = '' or locate(:q, lower(e.name)) > 0
                   or locate(:q, lower(e.city)) > 0
                   or locate(:q, lower(e.country)) > 0)
            """)
    Page<LocationJpaEntity> search(@Param("q") String q, @Param("type") LocationType type, Pageable pageable);
}
