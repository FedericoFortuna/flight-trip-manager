package com.flighttripmanager.budgets.api.dto;
public record ExpenseDto(java.util.UUID id,java.util.UUID tripId,java.util.UUID legId,String category,String description,String referenceKey,MoneyDto estimated,MoneyDto actual,java.time.Instant createdAt,java.time.Instant updatedAt,long tripVersion) {}
