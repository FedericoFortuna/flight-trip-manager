package com.flighttripmanager.budgets.application.contract;
import java.util.List;
public record BudgetPage<T>(List<T> items,int page,int size,long totalElements,long totalPages){}
