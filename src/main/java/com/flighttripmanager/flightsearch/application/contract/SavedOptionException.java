package com.flighttripmanager.flightsearch.application.contract;
public class SavedOptionException extends RuntimeException {
    public enum Reason { INVALID_REQUEST, NOT_FOUND, VERSION_CONFLICT, LIMIT_REACHED, LEG_CLOSED, INACTIVE, OFFER_UNAVAILABLE, OFFER_MISMATCH, CONFLICT }
    private final Reason reason;
    public SavedOptionException(Reason reason){super("Saved option operation failed");this.reason=reason;}
    public Reason reason(){return reason;}
}
