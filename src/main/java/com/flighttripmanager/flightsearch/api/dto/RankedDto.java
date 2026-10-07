package com.flighttripmanager.flightsearch.api.dto;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record RankedDto(OfferDto offer, BigDecimal score, String rankingGroup, Map<String,BigDecimal> penalties) {}
