package com.flighttripmanager.flights.application.mapper;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.mapstruct.*;
import com.flighttripmanager.flights.application.contract.*;
import com.flighttripmanager.flights.domain.model.*;
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FlightMapper {
    @Mapping(target="createdAt", source="createdAt") @Mapping(target="updatedAt", source="updatedAt")
    FlightSegment domain(FlightData data, UUID id, UUID tripId, UUID tripLegId,
        Instant originalScheduledDeparture, Instant originalScheduledArrival, Instant createdAt, Instant updatedAt);
    FlightData data(FlightSegment flight);
    @Mapping(target="data", source="flight")
    @Mapping(target="provider", constant="MANUAL")
    @Mapping(target="lastSyncedAt", ignore=true)
    FlightView view(FlightSegment flight, long tripVersion);
    FlightHistoryView view(FlightHistory history);
}
