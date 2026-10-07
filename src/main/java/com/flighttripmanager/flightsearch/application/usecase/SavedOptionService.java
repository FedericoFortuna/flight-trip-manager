package com.flighttripmanager.flightsearch.application.usecase;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.flighttripmanager.flightsearch.application.port.in.SavedOptions;
import com.flighttripmanager.flightsearch.application.port.out.*;
import com.flighttripmanager.flightsearch.application.contract.*;
import com.flighttripmanager.flightsearch.application.mapper.*;
import com.flighttripmanager.flightsearch.domain.model.*;
import static com.flighttripmanager.flightsearch.application.contract.SavedOptionException.Reason.*;
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class SavedOptionService implements SavedOptions {
    private final SavedOptionStore store;private final SavedOptionTrips trips;private final SearchSnapshots searches;
    private final SearchHistoryStore criteria;private final SearchMapper searchMapper;private final SavedOptionMapper mapper;private final Clock clock;
    public SavedOptionService(SavedOptionStore store,SavedOptionTrips trips,SearchSnapshots searches,SearchHistoryStore criteria,
            SearchMapper searchMapper,SavedOptionMapper mapper,Clock clock){
        this.store=store;this.trips=trips;this.searches=searches;this.criteria=criteria;this.searchMapper=searchMapper;this.mapper=mapper;this.clock=clock;
    }
    @Override @Transactional public SavedOptionView save(UUID tripId,UUID legId,SaveOption command){
        validate(command);var context=trips.inspect(tripId,legId,true);
        if(!context.acceptingAlternatives())throw error(LEG_CLOSED);
        var existing=store.list(tripId,legId);
        var replay=existing.stream().filter(o->store.observation(o.id(),command.searchId(),command.providerOfferId())
            .filter(p->p.provider().equals(command.provider())).isPresent()).findFirst();
        if(replay.isPresent())return mapper.view(replay.orElseThrow(),context.version(),searchMapper);
        version(command.version(),context.version());
        if(existing.size()>=3)throw error(LIMIT_REACHED);
        var selected=selected(command);
        trips.validateRoute(tripId,legId,selected.criteria().origin(),selected.criteria().destination());
        Set<Integer> used=new HashSet<>();existing.forEach(o->used.add(o.slot()));
        int slot=1;while(used.contains(slot))slot++;
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);if(now.isBefore(context.updatedAt()))now=context.updatedAt();
        var option=new SavedFlightOption(UUID.randomUUID(),tripId,legId,slot,selected.criteria(),selected.offer(),true,null,now,now);
        long revision=trips.advance(tripId,context.version());
        store.insert(option,FlightPriceSnapshot.observed(option.id(),command.searchId(),selected.offer()));
        return mapper.view(option,revision,searchMapper);
    }
    @Override public SavedOptionView get(UUID id){
        var option=require(id);var context=trips.inspect(option.tripId(),option.legId(),false);return mapper.view(option,context.version(),searchMapper);
    }
    @Override public SearchPage<SavedOptionView> list(UUID tripId,UUID legId,SearchQuery page){
        var context=trips.inspect(tripId,legId,false);var all=store.list(tripId,legId);
        return new SearchPage<>(all.stream().skip((long)page.page()*page.size()).limit(page.size()).map(o->mapper.view(o,context.version(),searchMapper)).toList(),
            page.page(),page.size(),all.size(),(all.size()+page.size()-1L)/page.size());
    }
    @Override @Transactional public PriceObservation observe(UUID id,SaveOption command){
        validate(command);var ref=reference(id);var context=trips.inspect(ref.tripId(),ref.legId(),true);var option=require(id);
        var replay=store.observation(id,command.searchId(),command.providerOfferId());
        if(replay.isPresent()&&replay.orElseThrow().provider().equals(command.provider()))
            return new PriceObservation(mapper.data(replay.orElseThrow()),context.version(),true);
        version(command.version(),context.version());
        if(!context.acceptingAlternatives())throw error(LEG_CLOSED);
        if(!option.active())throw error(INACTIVE);
        var selected=selected(command);
        if(!option.sameProduct(selected.criteria(),selected.offer()))throw error(OFFER_MISMATCH);
        var snapshot=FlightPriceSnapshot.observed(id,command.searchId(),selected.offer());
        long revision=trips.advance(ref.tripId(),context.version());store.append(snapshot);
        return new PriceObservation(mapper.data(snapshot),revision,false);
    }
    @Override public SearchPage<PriceSnapshotData> history(UUID id,SearchQuery page){
        require(id);var result=store.history(id,page);
        return new SearchPage<>(result.items().stream().map(mapper::data).toList(),result.page(),result.size(),result.totalElements(),result.totalPages());
    }
    @Override @Transactional public void delete(UUID id,long expectedVersion){
        if(expectedVersion<0)throw error(INVALID_REQUEST);
        var ref=reference(id);var context=trips.inspect(ref.tripId(),ref.legId(),true);version(expectedVersion,context.version());
        require(id);store.delete(id);trips.advance(ref.tripId(),context.version());
    }
    private record Selected(SearchCriteria criteria,SearchOffer offer){}
    private Selected selected(SaveOption command){
        var snapshot=searches.find(command.searchId()).filter(s->s.expiresAt().isAfter(clock.instant())).orElseThrow(()->error(OFFER_UNAVAILABLE));
        var matches=snapshot.offers().stream().map(SearchRanking.Ranked::offer).filter(o->o.provider().equals(command.provider())
            &&o.providerOfferId().equals(command.providerOfferId())&&o.expiresAt().isAfter(clock.instant())).toList();
        if(matches.size()!=1)throw error(OFFER_UNAVAILABLE);
        var original=criteria.find(command.searchId()).orElseThrow(()->error(OFFER_UNAVAILABLE));
        return new Selected(searchMapper.domain(original.criteria()),matches.get(0));
    }
    private SavedFlightOption require(UUID id){return store.find(id).orElseThrow(()->error(NOT_FOUND));}
    private SavedOptionStore.Reference reference(UUID id){return store.reference(id).orElseThrow(()->error(NOT_FOUND));}
    private static void validate(SaveOption command){
        if(command==null||command.version()==null||command.version()<0||command.searchId()==null
            ||command.provider()==null||command.provider().isBlank()||command.provider().length()>40
            ||command.providerOfferId()==null||command.providerOfferId().isBlank()||command.providerOfferId().length()>200)throw error(INVALID_REQUEST);
    }
    private static void version(long expected,long actual){if(expected!=actual)throw error(VERSION_CONFLICT);}
    private static SavedOptionException error(SavedOptionException.Reason reason){return new SavedOptionException(reason);}
}
