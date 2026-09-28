package com.flighttripmanager.flights.infrastructure.persistence.entity;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import com.flighttripmanager.flights.domain.model.*;
@Embeddable @Getter @Setter @NoArgsConstructor
public class BookingJpa {
    @Column(name="booking_reference", length=32)
    private String bookingReference;
    @Column(name="electronic_ticket_number", length=40)
    private String electronicTicketNumber;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="amount", column=@Column(name="price_paid_amount", precision=19, scale=2)),
        @AttributeOverride(name="currency", column=@Column(name="price_paid_currency", length=3)),
        @AttributeOverride(name="amountUsd", column=@Column(name="price_paid_amount_usd", precision=19, scale=2))
    })
    private MoneyJpa pricePaid;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="amount", column=@Column(name="ticket_total_amount", precision=19, scale=2)),
        @AttributeOverride(name="currency", column=@Column(name="ticket_total_currency", length=3)),
        @AttributeOverride(name="amountUsd", column=@Column(name="ticket_total_amount_usd", precision=19, scale=2))
    })
    private MoneyJpa ticketTotal;
    @Column(name="baggage", length=500)
    private String baggage;
    @Column(name="seat", length=20)
    private String seat;
}
