package com.flighttripmanager.budgets.domain.model;
import java.time.Instant;
import java.util.*;
public record TransportExpense(UUID id,UUID tripId,UUID legId,TransportCategory category,String description,
        String referenceKey,BudgetMoney estimated,BudgetMoney actual,Instant createdAt,Instant updatedAt){
    public TransportExpense {
        if(id==null||tripId==null||category==null||description==null||description.isBlank()||description.strip().length()>200
            ||estimated==null&&actual==null||createdAt==null||updatedAt==null||updatedAt.isBefore(createdAt))throw new BudgetRuleViolation();
        description=description.strip();
        if(referenceKey!=null){
            referenceKey=referenceKey.strip();if(referenceKey.isEmpty())referenceKey=null;
            else if(!referenceKey.matches("[A-Za-z0-9._:-]{1,100}"))throw new BudgetRuleViolation();
        }
    }
}
