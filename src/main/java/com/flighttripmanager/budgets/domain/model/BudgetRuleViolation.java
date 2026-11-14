package com.flighttripmanager.budgets.domain.model;
public class BudgetRuleViolation extends RuntimeException {
    public BudgetRuleViolation(){super("Invalid budget value");}
}
