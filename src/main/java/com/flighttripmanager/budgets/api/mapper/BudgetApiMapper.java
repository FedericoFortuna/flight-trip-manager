package com.flighttripmanager.budgets.api.mapper;
import org.mapstruct.*;
import com.flighttripmanager.budgets.application.contract.*;
import com.flighttripmanager.budgets.api.dto.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface BudgetApiMapper {
    PutBudget command(PutBudgetRequest request);
    PutLegBudget command(PutLegBudgetRequest request);
    ExpenseCommand command(ExpenseRequest request);
    BudgetDto response(BudgetView view);
    LegBudgetDto response(LegBudgetView view);
    ExpenseDto response(ExpenseView view);
    default BudgetPageDto<ExpenseDto> expenses(BudgetPage<ExpenseView> page){
        return new BudgetPageDto<>(page.items().stream().map(this::response).toList(),page.page(),page.size(),page.totalElements(),page.totalPages());
    }
    default BudgetPageDto<LegBudgetDto> legs(BudgetPage<LegBudgetView> page){
        return new BudgetPageDto<>(page.items().stream().map(this::response).toList(),page.page(),page.size(),page.totalElements(),page.totalPages());
    }
}
