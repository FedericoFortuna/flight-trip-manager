package com.flighttripmanager.flights.infrastructure.persistence.entity;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import com.flighttripmanager.flights.domain.model.*;
@Entity @Table(name="flight_passengers", schema="flights")
@Getter @Setter @NoArgsConstructor
public class FlightPassengerJpaEntity {
    @Id private UUID id;
    @Column(name="trip_id", nullable=false)
    private UUID tripId;
    @Column(name="flight_id", nullable=false)
    private UUID flightId;
    @Column(name="passenger_id", nullable=false)
    private UUID passengerId;
    @Column(name="seat", length=20)
    private String seat;
    @Column(name="baggage", length=500)
    private String baggage;
    @Column(name="electronic_ticket_number", length=40)
    private String electronicTicketNumber;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="amount", column=@Column(name="price_paid_amount", precision=19, scale=2)),
        @AttributeOverride(name="currency", column=@Column(name="price_paid_currency", length=3)),
        @AttributeOverride(name="amountUsd", column=@Column(name="price_paid_amount_usd", precision=19, scale=2))
    })
    private MoneyJpa pricePaid;
}
