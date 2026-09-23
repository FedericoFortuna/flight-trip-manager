package com.flighttripmanager.catalog.api.response;

import java.util.List;

public record CatalogPageResponse<T>(List<T> items, int page, int size, long totalElements, long totalPages) {
    public CatalogPageResponse { items = List.copyOf(items); }
}
