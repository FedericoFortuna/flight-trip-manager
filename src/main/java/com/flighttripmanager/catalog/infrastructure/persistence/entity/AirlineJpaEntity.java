package com.flighttripmanager.catalog.infrastructure.persistence.entity;

import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "airlines", schema = "catalog")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AirlineJpaEntity {
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
