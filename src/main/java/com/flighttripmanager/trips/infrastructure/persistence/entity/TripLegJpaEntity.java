package com.flighttripmanager.trips.infrastructure.persistence.entity;

import java.util.UUID;
import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import com.flighttripmanager.trips.domain.model.*;

@Entity
@Table(name = "trip_legs", schema = "trips")
@Getter @Setter @NoArgsConstructor
public class TripLegJpaEntity {
    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "origin_airport_id")
    private UUID originAirportId;

    @Column(name = "origin_location_id")
    private UUID originLocationId;

    @Column(name = "destination_airport_id")
    private UUID destinationAirportId;

    @Column(name = "destination_location_id")
    private UUID destinationLocationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false, length = 20)
    private TransportType transportType;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    @Column(name = "arrival_date")
    private LocalDate arrivalDate;

    @Column(name = "departure_date_time")
    private Instant departureDateTime;

    @Column(name = "arrival_date_time")
    private Instant arrivalDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_status", nullable = false, length = 20)
    private LegStatus declaredStatus;

    @Column(name = "manual_order")
    private Integer manualOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
