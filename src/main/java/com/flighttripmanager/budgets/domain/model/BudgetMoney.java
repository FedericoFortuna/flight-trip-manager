package com.flighttripmanager.budgets.domain.model;
import java.math.*;
import java.util.*;
public record BudgetMoney(BigDecimal amount,String currency,BigDecimal amountUsd,String conversionNote){
    public BudgetMoney {
        amount=money(amount);
        try{currency=currency.strip().toUpperCase(Locale.ROOT);Currency.getInstance(currency);}
        catch(IllegalArgumentException|NullPointerException e){throw new BudgetRuleViolation();}
        if("XXX".equals(currency))throw new BudgetRuleViolation();
        if(conversionNote!=null){conversionNote=conversionNote.strip();if(conversionNote.isEmpty())conversionNote=null;else if(conversionNote.length()>200)throw new BudgetRuleViolation();}
        if("USD".equals(currency)){
            if(amountUsd!=null&&amountUsd.compareTo(amount)!=0)throw new BudgetRuleViolation();
            amountUsd=amount;
        }else if(amountUsd!=null){
            amountUsd=money(amountUsd);if(conversionNote==null)throw new BudgetRuleViolation();
        }
    }
    public static BigDecimal money(BigDecimal amount){
        if(amount==null||amount.signum()<0||amount.scale()>4||(long)amount.precision()-amount.scale()>19)throw new BudgetRuleViolation();
        return amount.setScale(4);
    }
    public static BigDecimal limit(BigDecimal amount){
        if(amount==null)return null;
        if(amount.signum()<0||(long)amount.precision()-amount.scale()>17)throw new BudgetRuleViolation();
        try{amount=amount.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new BudgetRuleViolation();}
        if(amount.signum()<0||amount.precision()>19)throw new BudgetRuleViolation();return amount;
    }
}
