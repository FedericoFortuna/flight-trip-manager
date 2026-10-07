package com.flighttripmanager.flightsearch.domain.model;
public final class SearchRuleViolation extends RuntimeException {
    private final String field;
    public SearchRuleViolation(String field) { super("Invalid search field"); this.field=field; }
    public String field(){return field;}
}
