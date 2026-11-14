package com.flighttripmanager.budgets.api.dto;
public record BudgetDto(java.util.UUID tripId,long tripVersion,BalanceDto total,java.util.List<CategoryBudgetDto> categories,BalanceDto unallocated,java.math.BigDecimal categoryLimitsUsd,java.math.BigDecimal legLimitsUsd,String accountingScope,java.util.List<String> warnings) {}
