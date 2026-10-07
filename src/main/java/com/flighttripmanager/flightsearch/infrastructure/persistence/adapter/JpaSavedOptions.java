package com.flighttripmanager.flightsearch.infrastructure.persistence.adapter;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.*;
import com.flighttripmanager.flightsearch.domain.model.*;
import com.flighttripmanager.flightsearch.application.port.out.SavedOptionStore;
import com.flighttripmanager.flightsearch.application.contract.*;
import com.flighttripmanager.flightsearch.infrastructure.persistence.repository.*;
import com.flighttripmanager.flightsearch.infrastructure.persistence.mapper.SavedOptionPersistenceMapper;
@Component
public class JpaSavedOptions implements SavedOptionStore {
    private final SavedOptionJpaRepository options;private final FlightPriceJpaRepository prices;
    private final SavedOptionPersistenceMapper mapper;private final EntityManager em;
    public JpaSavedOptions(SavedOptionJpaRepository options,FlightPriceJpaRepository prices,SavedOptionPersistenceMapper mapper,EntityManager em){
        this.options=options;this.prices=prices;this.mapper=mapper;this.em=em;
    }
    @Override public Optional<Reference> reference(UUID id){return options.reference(id).map(r->new Reference(r.getTripId(),r.getLegId()));}
    @Override public Optional<SavedFlightOption> find(UUID id){return options.findById(id).map(mapper::domain);}
    @Override public List<SavedFlightOption> list(UUID tripId,UUID legId){return options.findByTripIdAndLegIdOrderBySlot(tripId,legId).stream().map(mapper::domain).toList();}
    @Override public void insert(SavedFlightOption option,FlightPriceSnapshot price){em.persist(mapper.entity(option));em.persist(mapper.entity(price));em.flush();}
    @Override public void append(FlightPriceSnapshot price){em.persist(mapper.entity(price));em.flush();}
    @Override public Optional<FlightPriceSnapshot> observation(UUID id,UUID searchId,String providerOfferId){
        return prices.findBySavedFlightOptionIdAndSourceSearchIdAndProviderOfferId(id,searchId,providerOfferId).map(mapper::domain);
    }
    @Override public SearchPage<FlightPriceSnapshot> history(UUID id,SearchQuery query){
        var page=prices.findBySavedFlightOptionId(id,PageRequest.of(query.page(),query.size(),Sort.by(Sort.Order.desc("observedAt"),Sort.Order.asc("id"))));
        return new SearchPage<>(page.getContent().stream().map(mapper::domain).toList(),page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
    }
    @Override public void delete(UUID id){prices.deleteBySavedFlightOptionId(id);prices.flush();options.deleteById(id);options.flush();}
    @Override public void close(UUID tripId,UUID legId,String reason,Instant at){options.close(tripId,legId,reason,at);}
}
