package com.flighttripmanager.budgets.domain.model;
import java.math.BigDecimal;
import java.util.UUID;
public record SpendingAggregate(TransportCategory category,UUID legId,String kind,String currency,
        BigDecimal originalAmount,BigDecimal knownUsd,long missingUsdCount){}
