package com.flighttripmanager.flights.infrastructure.persistence.entity;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import com.flighttripmanager.flights.domain.model.*;
@Embeddable @Getter @Setter @NoArgsConstructor
public class ScheduleJpa {
    @Column(name="scheduled_departure")
    private Instant scheduledDeparture;
    @Column(name="scheduled_arrival")
    private Instant scheduledArrival;
    @Column(name="estimated_departure")
    private Instant estimatedDeparture;
    @Column(name="estimated_arrival")
    private Instant estimatedArrival;
    @Column(name="actual_departure")
    private Instant actualDeparture;
    @Column(name="actual_arrival")
    private Instant actualArrival;
}
