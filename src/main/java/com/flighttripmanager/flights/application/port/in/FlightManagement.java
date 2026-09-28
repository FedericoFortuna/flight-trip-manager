package com.flighttripmanager.flights.application.port.in;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
public interface FlightManagement {
    FlightView create(UUID tripId, UUID legId, CreateFlight command);
    FlightView get(UUID id);
    FlightPage<FlightView> list(UUID tripId, UUID legId, FlightQuery query);
    FlightView patch(UUID id, FlightPatch patch);
    void delete(UUID id, long version);
    FlightPage<FlightHistoryView> history(UUID id, FlightQuery query);
}
