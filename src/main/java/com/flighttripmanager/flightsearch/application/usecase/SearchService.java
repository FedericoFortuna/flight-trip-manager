package com.flighttripmanager.flightsearch.application.usecase;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import com.flighttripmanager.flightsearch.application.contract.*;
import com.flighttripmanager.flightsearch.application.mapper.SearchMapper;
import com.flighttripmanager.flightsearch.application.port.in.FlightSearch;
import com.flighttripmanager.flightsearch.application.port.out.*;
import com.flighttripmanager.flightsearch.domain.model.*;

@Service
public class SearchService implements FlightSearch {
    public record Settings(Duration resultTtl,Duration deadline,int maxOffersPerVariant){}
    private final SearchMapper mapper;private final SearchAirports airports;private final List<FlightSearchProvider> providers;
    private final SearchSnapshots snapshots;private final SearchHistoryStore history;private final SearchRanking ranking;
    private final Clock clock;private final Settings settings;
    public SearchService(SearchMapper mapper,SearchAirports airports,List<FlightSearchProvider> providers,SearchSnapshots snapshots,
            SearchHistoryStore history,SearchRanking ranking,Clock clock,Settings settings){
        this.mapper=mapper;this.airports=airports;this.providers=providers;this.snapshots=snapshots;
        this.history=history;this.ranking=ranking;this.clock=clock;this.settings=settings;
    }
    @Override public SearchResult search(CriteriaData input,SearchQuery page){
        SearchCriteria criteria;
        try{criteria=mapper.domain(input);if(criteria==null)throw new SearchRuleViolation("request");}
        catch(SearchRuleViolation e){throw new SearchException(SearchException.Reason.INVALID_REQUEST,e.field());}
        catch(IllegalArgumentException e){throw new SearchException(SearchException.Reason.INVALID_REQUEST,"criteria");}
        var origin=airports.requireActive(criteria.origin());airports.requireActive(criteria.destination());
        LocalDate today=LocalDate.now(clock.withZone(origin.timezone()));
        if(criteria.departureDate().isBefore(today)||criteria.departureDate().isAfter(today.plusDays(365))
                ||criteria.returnDate()!=null&&criteria.returnDate().isAfter(today.plusDays(365)))
            throw new SearchException(SearchException.Reason.INVALID_REQUEST,"departureDate");
        var loaded=snapshots.load(criteria,()->execute(criteria,today));
        return page(loaded.snapshot(),page,loaded.cached());
    }
    private SearchSnapshots.Snapshot execute(SearchCriteria criteria,LocalDate today){
        UUID id=UUID.randomUUID();Instant now=clock.instant();
        // Persist only criteria in a short transaction. Never hold a DB transaction during network calls.
        history.save(new CriteriaHistory(id,now,mapper.data(criteria)));
        Instant deadline=now.plus(settings.deadline());List<SearchOffer> offers=new ArrayList<>();
        List<VariantData> variants=new ArrayList<>();List<String> warnings=new ArrayList<>();int succeeded=0;
        for(var dates:criteria.variants()){
            List<String> problems=new ArrayList<>();int count=0;boolean success=false;
            if(dates.departure().isBefore(today)||dates.departure().isAfter(today.plusDays(365))
                    ||dates.returning()!=null&&dates.returning().isAfter(today.plusDays(365))){
                variants.add(new VariantData(dates.offset(),dates.departure(),dates.returning(),"SKIPPED",0,List.of("DATE_OUTSIDE_SEARCH_WINDOW")));
                continue;
            }
            for(var provider:providers){
                if(!clock.instant().isBefore(deadline)){problems.add("SEARCH_DEADLINE");continue;}
                try{
                    var batch=provider.search(new FlightSearchProvider.Query(criteria,dates,deadline));success=true;succeeded++;
                    problems.addAll(batch.warnings());
                    List<SearchOffer> matching=batch.offers().stream().filter(o->o.expiresAt().isAfter(clock.instant())&&o.matches(criteria,dates)).toList();
                    if(matching.size()>settings.maxOffersPerVariant())problems.add("RESULT_LIMIT");
                    var limited=matching.stream().limit(settings.maxOffersPerVariant()).toList();
                    offers.addAll(limited);count+=limited.size();
                }catch(SearchProviderFailure e){problems.add(e.reason().name());}
            }
            variants.add(new VariantData(dates.offset(),dates.departure(),dates.returning(),success?"SUCCESS":"UNAVAILABLE",count,List.copyOf(problems)));
        }
        if(succeeded==0)throw new SearchException(SearchException.Reason.PROVIDER_UNAVAILABLE,"provider");
        if(offers.stream().anyMatch(o->o.synthetic()))warnings.add("SYNTHETIC_DATA");
        if(offers.stream().anyMatch(o->o.testMode()))warnings.add("TEST_MODE_NOT_BOOKABLE");
        warnings.add("PARTIAL_COSTS");warnings.add("PRICES_NOT_GUARANTEED");
        return new SearchSnapshots.Snapshot(id,now,clock.instant().plus(settings.resultTtl()),ranking.rank(offers),List.copyOf(variants),List.copyOf(warnings));
    }
    @Override public SearchResult results(UUID id,SearchQuery page){
        var snapshot=snapshots.find(id).orElseThrow(()->new SearchException(SearchException.Reason.RESULTS_EXPIRED,"searchId"));
        return page(snapshot,page,true);
    }
    @Override public SearchPage<CriteriaHistory> history(SearchQuery page){return history.list(page);}
    private SearchResult page(SearchSnapshots.Snapshot snapshot,SearchQuery query,boolean cached){
        if(!snapshot.expiresAt().isAfter(clock.instant()))throw new SearchException(SearchException.Reason.RESULTS_EXPIRED,"searchId");
        var live=snapshot.offers().stream().filter(r->r.offer().expiresAt().isAfter(clock.instant())).toList();
        List<String> warnings=new ArrayList<>(snapshot.warnings());
        if(live.size()!=snapshot.offers().size())warnings.add("EXPIRED_OFFERS_REMOVED");
        boolean partial=live.size()!=snapshot.offers().size()||snapshot.variants().stream().anyMatch(v->!v.status().equals("SUCCESS")||!v.warnings().isEmpty());
        return new SearchResult(snapshot.id(),snapshot.createdAt(),snapshot.expiresAt(),partial,cached,snapshot.variants(),
            live.stream().skip((long)query.page()*query.size()).limit(query.size()).map(mapper::data).toList(),
            query.page(),query.size(),live.size(),(live.size()+query.size()-1L)/query.size(),List.copyOf(warnings),"balance-v1");
    }
}
