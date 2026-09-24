package com.flighttripmanager.trips.api.response;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import com.flighttripmanager.trips.application.contract.*;
public record TripResponse(UUID id, String name, LocalDate startDate, LocalDate endDate, BigDecimal totalBudgetUsd,
        TripStatusValue status, TripStatusValue derivedStatus, TripStatusValue manualStatusOverride,
        long version, int legCount, Instant createdAt, Instant updatedAt) {}
