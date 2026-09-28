package com.flighttripmanager.flights.infrastructure.persistence.entity;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import com.flighttripmanager.flights.domain.model.*;
@Embeddable @Getter @Setter @NoArgsConstructor
public class OperationJpa {
    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false)
    private FlightStatus status;
    @Column(name="departure_terminal", length=40)
    private String departureTerminal;
    @Column(name="departure_gate", length=40)
    private String departureGate;
    @Column(name="arrival_terminal", length=40)
    private String arrivalTerminal;
    @Column(name="arrival_gate", length=40)
    private String arrivalGate;
}
