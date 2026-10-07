package com.flighttripmanager.flightsearch.infrastructure.cache;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import com.github.benmanes.caffeine.cache.*;
import com.flighttripmanager.flightsearch.domain.model.SearchCriteria;
import com.flighttripmanager.flightsearch.application.port.out.SearchSnapshots;
import com.flighttripmanager.flightsearch.application.contract.SearchException;
import com.flighttripmanager.flightsearch.infrastructure.configuration.SearchProperties;
@Component
public class CaffeineSearchSnapshots implements SearchSnapshots {
    private final Cache<SearchCriteria,Snapshot> criteria;
    private final Cache<UUID,Snapshot> ids;
    private final Semaphore slots;
    private final Clock clock;
    public CaffeineSearchSnapshots(SearchProperties p,Clock clock){
        this.clock=clock;this.slots=new Semaphore(p.getConcurrentSearches());
        criteria=Caffeine.newBuilder().maximumSize(p.getCacheEntries()).expireAfterWrite(Duration.ofSeconds(p.getResultTtlSeconds())).build();
        ids=Caffeine.newBuilder().maximumSize(p.getCacheEntries()).expireAfterWrite(Duration.ofSeconds(p.getResultTtlSeconds())).build();
    }
    @Override public Loaded load(SearchCriteria key,Supplier<Snapshot> supplier){
        AtomicBoolean hit=new AtomicBoolean(true);
        Snapshot result=criteria.asMap().compute(key,(k,old)->{
            if(old!=null&&old.expiresAt().isAfter(clock.instant()))return old;
            if(!slots.tryAcquire())throw new SearchException(SearchException.Reason.BUSY,"search");
            try{hit.set(false);return supplier.get();}finally{slots.release();}
        });
        ids.put(result.id(),result);
        return new Loaded(result,hit.get());
    }
    @Override public Optional<Snapshot> find(UUID id){
        return Optional.ofNullable(ids.getIfPresent(id)).filter(s->s.expiresAt().isAfter(clock.instant()));
    }
}
