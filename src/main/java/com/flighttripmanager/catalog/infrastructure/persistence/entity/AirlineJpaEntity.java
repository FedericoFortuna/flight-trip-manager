package com.flighttripmanager.catalog.infrastructure.persistence.entity;

import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "airlines", schema = "catalog")
@Getter
@lombok.Setter
@NoArgsConstructor
public class AirlineJpaEntity {
    @Column(length = 64)
    private String source;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "source_observed_at")
    private java.time.Instant sourceObservedAt;

    @Id
    private UUID id;

    @Column(name = "iata_code", nullable = false, length = 2)
    private String iataCode;

    @Column(name = "icao_code", length = 3)
    private String icaoCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
