package com.flighttripmanager.flightsearch.application.contract;
public final class SearchException extends RuntimeException {
    public enum Reason { INVALID_REQUEST, INVALID_REFERENCE, RESULTS_EXPIRED, PROVIDER_UNAVAILABLE, BUSY }
    private final Reason reason;private final String field;
    public SearchException(Reason reason,String field){super(reason.name());this.reason=reason;this.field=field;}
    public Reason reason(){return reason;}public String field(){return field;}
}
