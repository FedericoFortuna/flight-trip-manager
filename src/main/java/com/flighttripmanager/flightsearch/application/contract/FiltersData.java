package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record FiltersData(Set<String> airlines, Integer maxStops, LocalTime departureFrom, LocalTime departureTo, Integer maxDurationMinutes, boolean checkedBagRequired, Set<String> excludedAirports) {}
