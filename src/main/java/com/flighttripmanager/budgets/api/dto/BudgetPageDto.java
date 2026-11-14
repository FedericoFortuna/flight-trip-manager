package com.flighttripmanager.budgets.api.dto;
public record BudgetPageDto<T>(java.util.List<T> items,int page,int size,long totalElements,long totalPages) {}
