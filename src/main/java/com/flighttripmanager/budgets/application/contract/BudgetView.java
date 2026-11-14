package com.flighttripmanager.budgets.application.contract;
import java.math.BigDecimal;
import java.util.*;
public record BudgetView(UUID tripId,long tripVersion,BalanceData total,List<CategoryView> categories,BalanceData unallocated,
    BigDecimal categoryLimitsUsd,BigDecimal legLimitsUsd,String accountingScope,List<String> warnings){
    public record CategoryView(String category,BalanceData balance){}
}
