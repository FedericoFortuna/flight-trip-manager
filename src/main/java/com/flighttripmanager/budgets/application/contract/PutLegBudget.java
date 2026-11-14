package com.flighttripmanager.budgets.application.contract;
import java.math.BigDecimal;
public record PutLegBudget(Long version,BigDecimal amountUsd){}
