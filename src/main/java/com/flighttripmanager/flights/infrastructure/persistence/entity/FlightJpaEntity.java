package com.flighttripmanager.flights.infrastructure.persistence.entity;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import com.flighttripmanager.flights.domain.model.*;
@Entity @Table(name="flight_segments", schema="flights")
@Getter @Setter @NoArgsConstructor
public class FlightJpaEntity {
    @Id private UUID id;
    @Column(name="trip_id", nullable=false)
    private UUID tripId;
    @Column(name="trip_leg_id", nullable=false)
    private UUID tripLegId;
    @Column(name="transport_type", nullable=false, length=20)
    private String transportType;
    @Column(name="airline_id", nullable=false)
    private UUID airlineId;
    @Column(name="flight_number", nullable=false, length=10)
    private String flightNumber;
    @Column(name="flight_date", nullable=false)
    private LocalDate flightDate;
    @Column(name="origin_airport_id", nullable=false)
    private UUID originAirportId;
    @Column(name="destination_airport_id", nullable=false)
    private UUID destinationAirportId;
    @Embedded private ScheduleJpa schedule;
    @Embedded private OperationJpa operation;
    @Embedded private BookingJpa booking;
    @Enumerated(EnumType.STRING)
    @Column(name="connection_protection", nullable=false, length=20) private ConnectionProtection connectionProtection;
    @Column(name="original_scheduled_departure")
    private Instant originalScheduledDeparture;
    @Column(name="original_scheduled_arrival")
    private Instant originalScheduledArrival;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @Column(name="updated_at", nullable=false)
    private Instant updatedAt;
}
