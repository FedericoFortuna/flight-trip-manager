package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;

public record SearchPage<T>(List<T> items,int page,int size,long totalElements,long totalPages) {}
