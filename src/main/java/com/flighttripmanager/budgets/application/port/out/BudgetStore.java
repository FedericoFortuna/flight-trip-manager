package com.flighttripmanager.budgets.application.port.out;
import java.math.BigDecimal;
import java.util.*;
import com.flighttripmanager.budgets.domain.model.*;
import com.flighttripmanager.budgets.application.contract.*;
public interface BudgetStore {
    Map<TransportCategory,BigDecimal> categories(UUID tripId);
    Map<UUID,BigDecimal> legLimits(UUID tripId);
    void replaceCategories(UUID tripId,Map<TransportCategory,BigDecimal> limits);
    void setLegLimit(UUID tripId,UUID legId,BigDecimal limit);
    Optional<TransportExpense> expense(UUID tripId,UUID id);
    BudgetPage<TransportExpense> expenses(UUID tripId,BudgetQuery query);
    void save(TransportExpense expense,boolean insert);
    void delete(UUID tripId,UUID id);
    List<SpendingAggregate> aggregate(UUID tripId);
}
