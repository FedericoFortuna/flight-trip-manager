package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;

public record SearchResult(UUID searchId,Instant searchedAt,Instant expiresAt,boolean partial,boolean cached,
    List<VariantData> variants,List<RankedData> items,int page,int size,long totalElements,long totalPages,
    List<String> warnings,String rankingVersion) {}
