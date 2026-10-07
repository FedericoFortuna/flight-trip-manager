package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;

public record CriteriaHistory(UUID id,Instant createdAt,CriteriaData criteria) {}
