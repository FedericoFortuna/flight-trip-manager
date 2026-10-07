package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record RankedData(OfferData offer, BigDecimal score, String rankingGroup, Map<String,BigDecimal> penalties) {}
