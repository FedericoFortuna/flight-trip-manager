package com.flighttripmanager.budgets.application.contract;
import java.util.UUID;
public record LegBudgetView(UUID legId,String transportType,long tripVersion,BalanceData balance){}
