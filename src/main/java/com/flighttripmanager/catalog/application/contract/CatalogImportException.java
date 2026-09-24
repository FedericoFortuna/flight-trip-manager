package com.flighttripmanager.catalog.application.contract;

/** Safe error metadata: never includes input values or database messages. */
public final class CatalogImportException extends RuntimeException {
    public enum Reason { INVALID_BATCH, IDENTITY_CONFLICT, STALE_DATA, VERSION_CONFLICT, IMPORT_BUSY }
    private final Reason reason;
    private final String field;
    public CatalogImportException(Reason reason, String field) {
        super(reason.name());
        this.reason = reason;
        this.field = field;
    }
    public Reason reason() { return reason; }
    public String field() { return field; }
}
