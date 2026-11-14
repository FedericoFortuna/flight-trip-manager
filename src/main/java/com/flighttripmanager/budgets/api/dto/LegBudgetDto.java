package com.flighttripmanager.budgets.api.dto;
public record LegBudgetDto(java.util.UUID legId,String transportType,long tripVersion,BalanceDto balance) {}
