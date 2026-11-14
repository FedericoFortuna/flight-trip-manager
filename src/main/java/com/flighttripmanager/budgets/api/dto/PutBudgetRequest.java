package com.flighttripmanager.budgets.api.dto;
public record PutBudgetRequest(Long version,java.math.BigDecimal totalBudgetUsd,java.util.Map<String,java.math.BigDecimal> categories) {}
