package com.flighttripmanager.flights.infrastructure.persistence.mapper;
import java.util.*;
import org.mapstruct.*;
import com.flighttripmanager.flights.domain.model.*;
import com.flighttripmanager.flights.infrastructure.persistence.entity.*;
@Mapper(componentModel="spring", unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface FlightPersistenceMapper {
    @Mapping(target="transportType", constant="FLIGHT")
    FlightJpaEntity entity(FlightSegment flight);
    @Mapping(target="passengers", source="passengers")
    FlightSegment domain(FlightJpaEntity flight, List<FlightPassengerJpaEntity> passengers);
    FlightPassenger domain(FlightPassengerJpaEntity passenger);
    @Mapping(target="id", source="id")
    FlightPassengerJpaEntity entity(FlightPassenger passenger, UUID id, UUID flightId, UUID tripId);
    FlightHistoryJpaEntity entity(FlightHistory history);
    FlightHistory domain(FlightHistoryJpaEntity history);
}
