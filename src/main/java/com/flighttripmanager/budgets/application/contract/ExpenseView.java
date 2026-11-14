package com.flighttripmanager.budgets.application.contract;
import java.time.Instant;
import java.util.UUID;
public record ExpenseView(UUID id,UUID tripId,UUID legId,String category,String description,String referenceKey,
    MoneyData estimated,MoneyData actual,Instant createdAt,Instant updatedAt,long tripVersion){}
