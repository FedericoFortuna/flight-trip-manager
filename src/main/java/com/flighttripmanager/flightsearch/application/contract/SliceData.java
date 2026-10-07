package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record SliceData(List<SegmentData> segments, long durationMinutes, int stops) {}
