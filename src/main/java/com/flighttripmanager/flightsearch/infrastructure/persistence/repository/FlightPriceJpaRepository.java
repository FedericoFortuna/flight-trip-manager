package com.flighttripmanager.flightsearch.infrastructure.persistence.repository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.flighttripmanager.flightsearch.infrastructure.persistence.entity.FlightPriceJpaEntity;
public interface FlightPriceJpaRepository extends JpaRepository<FlightPriceJpaEntity,UUID> {
    Page<FlightPriceJpaEntity> findBySavedFlightOptionId(UUID id,Pageable page);
    Optional<FlightPriceJpaEntity> findBySavedFlightOptionIdAndSourceSearchIdAndProviderOfferId(UUID optionId,UUID searchId,String providerOfferId);
    void deleteBySavedFlightOptionId(UUID id);
}
