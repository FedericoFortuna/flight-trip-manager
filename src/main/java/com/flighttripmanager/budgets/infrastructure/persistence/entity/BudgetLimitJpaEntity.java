package com.flighttripmanager.budgets.infrastructure.persistence.entity;
import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(schema="budgets",name="budget_limits") @Getter @Setter @NoArgsConstructor
public class BudgetLimitJpaEntity {
    @Id private UUID id;
    private UUID tripId;
    private String scope;
    private String category;
    private UUID legId;
    @Column(precision=19,scale=2) private BigDecimal amountUsd;
}
