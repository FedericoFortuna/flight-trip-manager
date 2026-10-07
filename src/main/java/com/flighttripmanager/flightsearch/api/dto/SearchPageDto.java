package com.flighttripmanager.flightsearch.api.dto;
import java.util.List;
public record SearchPageDto<T>(List<T> items,int page,int size,long totalElements,long totalPages) {}
