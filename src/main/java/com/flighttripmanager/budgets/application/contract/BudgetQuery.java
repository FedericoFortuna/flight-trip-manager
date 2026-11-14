package com.flighttripmanager.budgets.application.contract;
public record BudgetQuery(int page,int size){
    public BudgetQuery {if(page<0||page>1000000||size<1||size>100)throw new BudgetException(BudgetException.Reason.INVALID_REQUEST);}
}
