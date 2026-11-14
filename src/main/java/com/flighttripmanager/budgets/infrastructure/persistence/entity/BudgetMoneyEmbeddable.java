package com.flighttripmanager.budgets.infrastructure.persistence.entity;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
@Embeddable @Getter @Setter @NoArgsConstructor
public class BudgetMoneyEmbeddable {
    @Column(precision=23,scale=4) private BigDecimal amount;
    private String currency;
    @Column(precision=23,scale=4) private BigDecimal amountUsd;
    private String conversionNote;
}
