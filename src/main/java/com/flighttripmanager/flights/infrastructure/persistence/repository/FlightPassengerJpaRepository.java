package com.flighttripmanager.flights.infrastructure.persistence.repository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.flighttripmanager.flights.infrastructure.persistence.entity.*;

public interface FlightPassengerJpaRepository extends JpaRepository<FlightPassengerJpaEntity, UUID> {
    List<FlightPassengerJpaEntity> findByFlightId(UUID flightId);
    List<FlightPassengerJpaEntity> findByFlightIdIn(Collection<UUID> ids);
    void deleteByFlightId(UUID flightId);
}
