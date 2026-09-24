package com.flighttripmanager.trips.api.request;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import com.flighttripmanager.trips.application.contract.*;
public record CreateTripRequest(String name, LocalDate startDate, LocalDate endDate, BigDecimal totalBudgetUsd, TripStatusValue manualStatusOverride) {}
