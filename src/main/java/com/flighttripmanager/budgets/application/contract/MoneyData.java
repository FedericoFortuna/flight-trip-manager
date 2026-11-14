package com.flighttripmanager.budgets.application.contract;
import java.math.BigDecimal;
public record MoneyData(BigDecimal amount,String currency,BigDecimal amountUsd,String conversionNote){}
