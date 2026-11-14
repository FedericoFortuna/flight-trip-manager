package com.flighttripmanager.budgets.domain.model;
public enum TransportCategory {
    FLIGHTS("FLIGHT"),TRAINS("TRAIN"),BUSES("BUS"),CAR("CAR"),OTHER_TRANSPORT("OTHER");
    private final String transport;
    TransportCategory(String transport){this.transport=transport;}
    public String transport(){return transport;}
}
