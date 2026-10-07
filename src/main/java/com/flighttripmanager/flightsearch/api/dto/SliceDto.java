package com.flighttripmanager.flightsearch.api.dto;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record SliceDto(List<SegmentDto> segments, long durationMinutes, int stops) {}
