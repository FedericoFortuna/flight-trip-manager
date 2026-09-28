package com.flighttripmanager.flights.infrastructure.persistence.entity;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import com.flighttripmanager.flights.domain.model.*;
@Embeddable @Getter @Setter @NoArgsConstructor
public class MoneyJpa {
    @Column(name="amount", precision=19, scale=2)
    private BigDecimal amount;
    @Column(name="currency", length=3)
    private String currency;
    @Column(name="amount_usd", precision=19, scale=2)
    private BigDecimal amountUsd;
}
