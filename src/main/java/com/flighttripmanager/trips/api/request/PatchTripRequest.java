package com.flighttripmanager.trips.api.request;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import com.flighttripmanager.trips.application.contract.*;
/** OpenAPI schema for the flat merge-patch body; presence is handled by TripPatchReader. */
public record PatchTripRequest(Long version, String name, LocalDate startDate, LocalDate endDate, BigDecimal totalBudgetUsd, TripStatusValue manualStatusOverride) {}
