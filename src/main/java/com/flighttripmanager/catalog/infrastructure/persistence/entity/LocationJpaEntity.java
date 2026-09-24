package com.flighttripmanager.catalog.infrastructure.persistence.entity;

import java.util.UUID;
import com.flighttripmanager.catalog.domain.model.LocationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "locations", schema = "catalog")
@Getter
@lombok.Setter
@NoArgsConstructor
public class LocationJpaEntity {
    @Column(length = 64)
    private String source;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "source_observed_at")
    private java.time.Instant sourceObservedAt;

    @Column(name = "last_synced_at")
    private java.time.Instant lastSyncedAt;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationType type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 2)
    private String country;
}
