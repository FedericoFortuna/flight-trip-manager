package com.flighttripmanager.catalog.application.contract;

import java.util.List;

public record CatalogPage<T>(List<T> items, int page, int size, long totalElements) {
    public CatalogPage {
        items = List.copyOf(items);
        if (page < 0 || size < 1 || totalElements < 0) throw new IllegalArgumentException("Invalid page");
    }
    public long totalPages() {
        return totalElements / size + (totalElements % size == 0 ? 0 : 1);
    }
}

