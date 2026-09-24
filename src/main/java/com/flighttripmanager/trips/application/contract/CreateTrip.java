package com.flighttripmanager.trips.application.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
public record CreateTrip(String name, LocalDate startDate, LocalDate endDate, BigDecimal totalBudgetUsd,
        TripStatusValue manualStatusOverride) {}
