package com.flighttripmanager.flightsearch.api.dto;
import java.time.Instant;import java.util.UUID;
public record HistoryDto(UUID id,Instant createdAt,CriteriaDto criteria) {}
