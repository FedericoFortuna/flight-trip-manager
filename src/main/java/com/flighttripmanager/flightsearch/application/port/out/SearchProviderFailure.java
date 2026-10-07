package com.flighttripmanager.flightsearch.application.port.out;
public final class SearchProviderFailure extends RuntimeException {
    public enum Reason { DISABLED, AUTHENTICATION, REJECTED, RATE_LIMITED, CIRCUIT_OPEN, TIMEOUT, UNAVAILABLE, INVALID_RESPONSE, BUSY }
    private final Reason reason;
    public SearchProviderFailure(Reason reason){super(reason.name());this.reason=reason;}
    public Reason reason(){return reason;}
}
