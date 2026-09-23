package com.flighttripmanager.catalog.application.contract;

public final class CatalogNotFoundException extends RuntimeException {
    private final String code;
    public CatalogNotFoundException(String code) {
        super("Catalog resource not found");
        this.code = code;
    }
    public String code() { return code; }
}

