package com.flighttripmanager.budgets.application.port.out;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
public interface BudgetTrips {
    record Leg(UUID id,String transportType){}
    record Context(long version,Instant updatedAt,BigDecimal totalBudgetUsd,List<Leg> legs){}
    Context inspect(UUID tripId,boolean lock);
    long update(UUID tripId,long version,BigDecimal total);
}
