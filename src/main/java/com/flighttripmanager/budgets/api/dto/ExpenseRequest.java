package com.flighttripmanager.budgets.api.dto;
public record ExpenseRequest(Long version,java.util.UUID legId,String category,String description,String referenceKey,MoneyDto estimated,MoneyDto actual) {}
