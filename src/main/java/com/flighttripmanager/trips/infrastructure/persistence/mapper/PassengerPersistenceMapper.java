package com.flighttripmanager.trips.infrastructure.persistence.mapper;
import org.mapstruct.*;
import com.flighttripmanager.trips.domain.model.Passenger;
import com.flighttripmanager.trips.infrastructure.persistence.entity.PassengerJpaEntity;
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PassengerPersistenceMapper {
    Passenger toDomain(PassengerJpaEntity entity);
    PassengerJpaEntity toEntity(Passenger passenger);
}
