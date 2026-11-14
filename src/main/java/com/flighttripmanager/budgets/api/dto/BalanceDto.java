package com.flighttripmanager.budgets.api.dto;
public record BalanceDto(java.math.BigDecimal limitUsd,SpendingDto estimated,SpendingDto actual,SpendingDto forecast,java.math.BigDecimal remainingUsd,java.math.BigDecimal remainingAfterActualUsd) {}
