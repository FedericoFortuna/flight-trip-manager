package com.flighttripmanager.budgets.infrastructure.persistence.entity;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(schema="budgets",name="transport_expenses") @Getter @Setter @NoArgsConstructor
public class ExpenseJpaEntity {
    @Id private UUID id;
    private UUID tripId;
    private UUID legId;
    private String transportType;
    private String category;
    private String description;
    private String referenceKey;
    @Embedded @AttributeOverrides({
        @AttributeOverride(name="amount",column=@Column(name="estimated_amount",precision=23,scale=4)),
        @AttributeOverride(name="currency",column=@Column(name="estimated_currency")),
        @AttributeOverride(name="amountUsd",column=@Column(name="estimated_amount_usd",precision=23,scale=4)),
        @AttributeOverride(name="conversionNote",column=@Column(name="estimated_conversion_note"))
    })
    private BudgetMoneyEmbeddable estimated;
    @Embedded @AttributeOverrides({
        @AttributeOverride(name="amount",column=@Column(name="actual_amount",precision=23,scale=4)),
        @AttributeOverride(name="currency",column=@Column(name="actual_currency")),
        @AttributeOverride(name="amountUsd",column=@Column(name="actual_amount_usd",precision=23,scale=4)),
        @AttributeOverride(name="conversionNote",column=@Column(name="actual_conversion_note"))
    })
    private BudgetMoneyEmbeddable actual;
    private Instant createdAt;
    private Instant updatedAt;
}
