package com.flighttripmanager.budgets.application.contract;
import java.math.BigDecimal;
import java.util.Map;
public record BalanceData(BigDecimal limitUsd,SpendingData estimated,SpendingData actual,SpendingData forecast,
    BigDecimal remainingUsd,BigDecimal remainingAfterActualUsd){
    public record SpendingData(BigDecimal knownUsd,long missingUsdCount,Map<String,BigDecimal> originalAmounts){}
}
