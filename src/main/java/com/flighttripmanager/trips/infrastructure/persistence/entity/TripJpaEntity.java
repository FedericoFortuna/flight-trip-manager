package com.flighttripmanager.trips.infrastructure.persistence.entity;

import java.util.UUID;
import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import com.flighttripmanager.trips.domain.model.*;

@Entity
@Table(name = "trips", schema = "trips")
@Getter @Setter @NoArgsConstructor
public class TripJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_budget_usd", precision = 19, scale = 2)
    private BigDecimal totalBudgetUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "manual_status_override", length = 30)
    private TripStatus manualStatusOverride;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
