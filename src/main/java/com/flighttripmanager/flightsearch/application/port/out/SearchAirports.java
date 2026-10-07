package com.flighttripmanager.flightsearch.application.port.out;
import java.time.ZoneId;
public interface SearchAirports {
    record Airport(String iata,ZoneId timezone){}
    Airport requireActive(String iata);
}
