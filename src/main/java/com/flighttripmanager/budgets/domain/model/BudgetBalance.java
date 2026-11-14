package com.flighttripmanager.budgets.domain.model;
import java.math.BigDecimal;
import java.util.*;
public record BudgetBalance(BigDecimal limitUsd,Spending estimated,Spending actual,Spending forecast,
        BigDecimal remainingUsd,BigDecimal remainingAfterActualUsd){
    public record Spending(BigDecimal knownUsd,long missingUsdCount,Map<String,BigDecimal> originalAmounts){}
    public static BudgetBalance calculate(BigDecimal limit,List<SpendingAggregate> rows){
        var estimated=total(rows,"ESTIMATED");var actual=total(rows,"ACTUAL");var forecast=total(rows,"FORECAST");
        return new BudgetBalance(limit,estimated,actual,forecast,
            limit==null||forecast.missingUsdCount()>0?null:limit.subtract(forecast.knownUsd()),
            limit==null||actual.missingUsdCount()>0?null:limit.subtract(actual.knownUsd()));
    }
    private static Spending total(List<SpendingAggregate> rows,String kind){
        BigDecimal usd=BigDecimal.ZERO.setScale(4);long missing=0;Map<String,BigDecimal> amounts=new TreeMap<>();
        for(var row:rows)if(row.kind().equals(kind)){
            usd=usd.add(row.knownUsd());missing=Math.addExact(missing,row.missingUsdCount());
            amounts.merge(row.currency(),row.originalAmount(),BigDecimal::add);
        }
        return new Spending(usd,missing,Collections.unmodifiableMap(amounts));
    }
}
