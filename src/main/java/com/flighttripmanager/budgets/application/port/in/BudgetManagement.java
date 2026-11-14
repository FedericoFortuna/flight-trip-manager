package com.flighttripmanager.budgets.application.port.in;
import java.util.UUID;
import com.flighttripmanager.budgets.application.contract.*;
public interface BudgetManagement {
    BudgetView get(UUID tripId);
    BudgetView put(UUID tripId,PutBudget command);
    LegBudgetView leg(UUID tripId,UUID legId);
    LegBudgetView putLeg(UUID tripId,UUID legId,PutLegBudget command);
    BudgetPage<LegBudgetView> legs(UUID tripId,BudgetQuery query);
    ExpenseView createExpense(UUID tripId,ExpenseCommand command);
    ExpenseView replaceExpense(UUID tripId,UUID id,ExpenseCommand command);
    ExpenseView expense(UUID tripId,UUID id);
    BudgetPage<ExpenseView> expenses(UUID tripId,BudgetQuery query);
    void deleteExpense(UUID tripId,UUID id,long version);
}
