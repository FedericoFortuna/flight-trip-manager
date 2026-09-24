package com.flighttripmanager.trips.application.mapper;

import java.time.*;
import java.util.UUID;
import org.mapstruct.*;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.domain.model.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TripMapper {
    @Mapping(target = "status", expression = "java(tripStatus(trip.status(now)))")
    @Mapping(target = "derivedStatus", expression = "java(tripStatus(trip.derivedStatus(now)))")
    @Mapping(target = "legCount", expression = "java(trip.legs().size())")
    TripView view(Trip trip, @Context Instant now);
    @Mapping(target = "status", expression = "java(legStatus(leg.status(now)))")
    @Mapping(target = "tripVersion", source = "version")
    LegView view(TripLeg leg, long version, @Context Instant now);
    TripStatus tripStatus(TripStatusValue value);
    TripStatusValue tripStatus(TripStatus value);
    LegStatus legStatus(LegStatusValue value);
    LegStatusValue legStatus(LegStatus value);
    TransportType transport(TransportTypeValue value);
    TransportTypeValue transport(TransportType value);
    Place place(PlaceRef value);
    PlaceRef place(Place value);
    @Mapping(target = "declaredStatus", source = "command.status")
    @Mapping(target = "createdAt", source = "now")
    @Mapping(target = "updatedAt", source = "now")
    TripLeg leg(CreateLeg command, UUID id, UUID tripId, Integer manualOrder, Instant now);
}
