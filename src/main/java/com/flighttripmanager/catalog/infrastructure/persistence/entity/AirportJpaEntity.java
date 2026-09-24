package com.flighttripmanager.catalog.infrastructure.persistence.entity;

import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "airports", schema = "catalog")
@Getter
@lombok.Setter
@NoArgsConstructor
public class AirportJpaEntity {
    @Column(length = 64)
    private String source;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "source_observed_at")
    private java.time.Instant sourceObservedAt;

    @Id
    private UUID id;

    @Column(name = "iata_code", nullable = false, length = 3)
    private String iataCode;

    @Column(name = "icao_code", length = 4)
    private String icaoCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, length = 100)
    private String timezone;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
