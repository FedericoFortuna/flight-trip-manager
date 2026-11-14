package com.flighttripmanager.budgets.application.contract;
import java.math.BigDecimal;
import java.util.Map;
public record PutBudget(Long version,BigDecimal totalBudgetUsd,Map<String,BigDecimal> categories){}
