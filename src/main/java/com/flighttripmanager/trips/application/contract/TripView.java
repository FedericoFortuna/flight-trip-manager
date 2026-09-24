package com.flighttripmanager.trips.application.contract;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record TripView(UUID id, String name, LocalDate startDate, LocalDate endDate, BigDecimal totalBudgetUsd,
        TripStatusValue status, TripStatusValue derivedStatus, TripStatusValue manualStatusOverride,
        long version, int legCount, Instant createdAt, Instant updatedAt) {}
