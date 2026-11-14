package com.flighttripmanager.budgets.infrastructure.persistence.mapper;
import org.mapstruct.*;
import com.flighttripmanager.budgets.domain.model.*;
import com.flighttripmanager.budgets.infrastructure.persistence.entity.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface ExpensePersistenceMapper {
    @Mapping(target="transportType",expression="java(expense.category().transport())")
    ExpenseJpaEntity entity(TransportExpense expense);
    TransportExpense domain(ExpenseJpaEntity entity);
}
