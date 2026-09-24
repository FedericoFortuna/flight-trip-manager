package com.flighttripmanager.trips.application.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
public record PatchTrip(Long version, Change<String> name, Change<LocalDate> startDate, Change<LocalDate> endDate,
        Change<BigDecimal> totalBudgetUsd, Change<TripStatusValue> manualStatusOverride) {}
