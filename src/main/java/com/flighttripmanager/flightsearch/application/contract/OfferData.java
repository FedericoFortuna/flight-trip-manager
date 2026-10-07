package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record OfferData(String provider, String providerOfferId, String ownerAirline, boolean synthetic, boolean testMode, BigDecimal amount, String currency, BigDecimal amountUsd, String checkedBag, Instant observedAt, Instant expiresAt, List<SliceData> slices, List<String> missingCosts) {}
