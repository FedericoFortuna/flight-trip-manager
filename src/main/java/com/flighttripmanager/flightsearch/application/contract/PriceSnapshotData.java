package com.flighttripmanager.flightsearch.application.contract;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record PriceSnapshotData(UUID id,UUID savedFlightOptionId,UUID sourceSearchId,String providerOfferId,
        BigDecimal basePrice,BigDecimal baggagePrice,BigDecimal seatPrice,BigDecimal totalPrice,
        String originalCurrency,BigDecimal originalAmount,BigDecimal amountUsd,String provider,
        Instant observedAt,boolean synthetic,boolean testMode,List<String> missingCosts) {}
