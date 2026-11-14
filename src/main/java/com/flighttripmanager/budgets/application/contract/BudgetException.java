package com.flighttripmanager.budgets.application.contract;
public class BudgetException extends RuntimeException {
    public enum Reason { INVALID_REQUEST,NOT_FOUND,INVALID_LEG,VERSION_CONFLICT,DUPLICATE_REFERENCE,CONFLICT }
    private final Reason reason;
    public BudgetException(Reason reason){super("Budget operation failed");this.reason=reason;}
    public Reason reason(){return reason;}
}
