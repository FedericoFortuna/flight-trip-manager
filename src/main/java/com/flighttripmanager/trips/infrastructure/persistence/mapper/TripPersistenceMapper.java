package com.flighttripmanager.trips.infrastructure.persistence.mapper;

import java.util.*;
import org.mapstruct.*;
import com.flighttripmanager.trips.domain.model.*;
import com.flighttripmanager.trips.infrastructure.persistence.entity.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TripPersistenceMapper {
    Trip toDomain(TripJpaEntity entity, List<TripLegJpaEntity> legs);
    TripJpaEntity toEntity(Trip trip);
    @Mapping(target = "origin", expression = "java(place(entity.getOriginAirportId(), entity.getOriginLocationId()))")
    @Mapping(target = "destination", expression = "java(place(entity.getDestinationAirportId(), entity.getDestinationLocationId()))")
    TripLeg toDomain(TripLegJpaEntity entity);
    @Mapping(target = "originAirportId", source = "origin", qualifiedByName = "airportId")
    @Mapping(target = "originLocationId", source = "origin", qualifiedByName = "locationId")
    @Mapping(target = "destinationAirportId", source = "destination", qualifiedByName = "airportId")
    @Mapping(target = "destinationLocationId", source = "destination", qualifiedByName = "locationId")
    TripLegJpaEntity toEntity(TripLeg leg);
    default Place place(UUID airport, UUID location) {
        return airport != null ? new Place(PlaceType.AIRPORT, airport) : new Place(PlaceType.LOCATION, location);
    }
    @Named("airportId") default UUID airportId(Place place) { return place.kind() == PlaceType.AIRPORT ? place.id() : null; }
    @Named("locationId") default UUID locationId(Place place) { return place.kind() == PlaceType.LOCATION ? place.id() : null; }
}
