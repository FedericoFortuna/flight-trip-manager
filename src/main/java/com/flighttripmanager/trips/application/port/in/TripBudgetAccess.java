package com.flighttripmanager.trips.application.port.in;
import java.util.UUID;
import java.math.BigDecimal;
import com.flighttripmanager.trips.application.contract.BudgetTripContext;
public interface TripBudgetAccess {
    BudgetTripContext inspectBudget(UUID tripId,boolean lock);
    long updateBudget(UUID tripId,long version,BigDecimal totalBudgetUsd);
}
