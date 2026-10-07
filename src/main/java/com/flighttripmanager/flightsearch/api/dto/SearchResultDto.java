package com.flighttripmanager.flightsearch.api.dto;
import java.time.Instant;import java.util.*;
public record SearchResultDto(UUID searchId,Instant searchedAt,Instant expiresAt,boolean partial,boolean cached,
    List<VariantDto> variants,List<RankedDto> items,int page,int size,long totalElements,long totalPages,
    List<String> warnings,String rankingVersion) {}
