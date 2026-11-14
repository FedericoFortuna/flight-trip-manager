package com.flighttripmanager.budgets.application.contract;
import java.util.UUID;
public record ExpenseCommand(Long version,UUID legId,String category,String description,String referenceKey,MoneyData estimated,MoneyData actual){}
