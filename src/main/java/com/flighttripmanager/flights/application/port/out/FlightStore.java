package com.flighttripmanager.flights.application.port.out;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
import com.flighttripmanager.flights.domain.model.*;
public interface FlightStore {
    record Reference(UUID tripId, UUID legId) {}
    Optional<Reference> reference(UUID id);
    Optional<FlightSegment> find(UUID id);
    FlightPage<FlightSegment> list(UUID tripId, UUID legId, FlightQuery query);
    void save(FlightSegment flight, List<FlightHistory> history);
    void insert(FlightSegment flight, List<FlightHistory> history);
    void delete(UUID id);
    FlightPage<FlightHistory> history(UUID id, FlightQuery query);
}
