package com.flighttripmanager.flightsearch.api.dto;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record OfferDto(String provider, String providerOfferId, String ownerAirline, boolean synthetic, boolean testMode, BigDecimal amount, String currency, BigDecimal amountUsd, String checkedBag, Instant observedAt, Instant expiresAt, List<SliceDto> slices, List<String> missingCosts) {}
