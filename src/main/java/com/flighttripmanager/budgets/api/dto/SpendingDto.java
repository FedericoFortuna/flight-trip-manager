package com.flighttripmanager.budgets.api.dto;
public record SpendingDto(java.math.BigDecimal knownUsd,long missingUsdCount,java.util.Map<String,java.math.BigDecimal> originalAmounts) {}
