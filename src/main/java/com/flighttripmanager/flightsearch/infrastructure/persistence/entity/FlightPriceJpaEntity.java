package com.flighttripmanager.flightsearch.infrastructure.persistence.entity;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
@Entity @Table(schema="flightsearch",name="flight_price_snapshots")
@Getter @Setter @NoArgsConstructor
public class FlightPriceJpaEntity {
    @Id private UUID id;
    private UUID savedFlightOptionId;
    private UUID sourceSearchId;
    private String providerOfferId;
    @Column(precision=23,scale=4) private BigDecimal basePrice;
    @Column(precision=23,scale=4) private BigDecimal baggagePrice;
    @Column(precision=23,scale=4) private BigDecimal seatPrice;
    @Column(precision=23,scale=4) private BigDecimal totalPrice;
    private String originalCurrency;
    @Column(precision=23,scale=4) private BigDecimal originalAmount;
    @Column(precision=23,scale=4) private BigDecimal amountUsd;
    private String provider;
    private Instant observedAt;
    private boolean synthetic;
    private boolean testMode;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private List<String> missingCosts;
}
