package com.flighttripmanager.budgets.application.mapper;
import org.mapstruct.*;
import com.flighttripmanager.budgets.application.contract.*;
import com.flighttripmanager.budgets.domain.model.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface BudgetMapper {
    BudgetMoney money(MoneyData data);
    BalanceData balance(BudgetBalance balance);
    @Mapping(target="tripVersion",source="version")
    ExpenseView view(TransportExpense expense,long version);
}
