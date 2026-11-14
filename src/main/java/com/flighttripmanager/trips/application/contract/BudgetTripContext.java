package com.flighttripmanager.trips.application.contract;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
public record BudgetTripContext(long version,Instant updatedAt,BigDecimal totalBudgetUsd,List<Leg> legs){
    public record Leg(UUID id,String transportType){}
}
