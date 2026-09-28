package com.flighttripmanager.flights.infrastructure.persistence.entity;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import com.flighttripmanager.flights.domain.model.*;
@Entity @Table(name="flight_history", schema="flights")
@Getter @Setter @NoArgsConstructor
public class FlightHistoryJpaEntity {
    @Id private UUID id;
    @Column(name="flight_id", nullable=false)
    private UUID flightId;
    @Column(name="revision", nullable=false)
    private long revision;
    @Column(name="field_name", nullable=false, length=80)
    private String field;
    @Column(name="previous_value", length=80)
    private String previousValue;
    @Column(name="new_value", length=80)
    private String value;
    @Column(name="source", nullable=false, length=80)
    private String source;
    @Column(name="recorded_at", nullable=false)
    private Instant recordedAt;
}
