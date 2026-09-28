package com.flighttripmanager.flights.infrastructure.persistence.repository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.flighttripmanager.flights.infrastructure.persistence.entity.*;

public interface FlightHistoryJpaRepository extends JpaRepository<FlightHistoryJpaEntity, UUID> {
    Page<FlightHistoryJpaEntity> findByFlightId(UUID flightId, Pageable pageable);
    void deleteByFlightId(UUID flightId);
}
