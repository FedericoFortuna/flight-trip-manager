package com.flighttripmanager.flightsearch;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import com.flighttripmanager.flightsearch.application.contract.*;
import com.flighttripmanager.flightsearch.application.mapper.SearchMapper;
import com.flighttripmanager.flightsearch.application.port.out.*;
import com.flighttripmanager.flightsearch.application.usecase.SearchService;
import com.flighttripmanager.flightsearch.domain.model.*;
import com.flighttripmanager.flightsearch.infrastructure.cache.CaffeineSearchSnapshots;
import com.flighttripmanager.flightsearch.infrastructure.configuration.SearchProperties;
import testsupport.TripsClockConfiguration.MutableClock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SearchServiceTest {
    final MutableClock clock=new MutableClock();
    final SearchProperties properties=new SearchProperties();
    final SearchHistoryStore history=mock(SearchHistoryStore.class);
    final SearchMapper mapper=Mappers.getMapper(SearchMapper.class);
    final SearchAirports airports=iata->new SearchAirports.Airport(iata,ZoneOffset.UTC);
    final SearchRanking ranking=new SearchRanking(new SearchRanking.Weights(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE));
    SearchService service(FlightSearchProvider provider){
        return new SearchService(mapper,airports,List.of(provider),new CaffeineSearchSnapshots(properties,clock),history,ranking,clock,new SearchService.Settings(Duration.ofSeconds(180),Duration.ofSeconds(45),2));
    }
    CriteriaData criteria(int flex){return mapper.data(SearchDomainTest.criteria(flex,null,null));}
    @Test void cachesSuccessfulPartialResultsAndRecordsFailedDates(){
        AtomicInteger calls=new AtomicInteger();
        var service=service(q->{calls.incrementAndGet();if(q.dates().offset()!=0)throw new SearchProviderFailure(SearchProviderFailure.Reason.TIMEOUT);
            return new FlightSearchProvider.Batch(List.of(SearchDomainTest.offer("a","USD",100,9,4,BagAvailability.INCLUDED)),List.of());});
        var result=service.search(criteria(3),new SearchQuery(0,20));
        assertThat(result.partial()).isTrue();assertThat(result.items()).hasSize(1);assertThat(result.variants()).hasSize(7);
        assertThat(service.search(criteria(3),new SearchQuery(0,20)).searchId()).isEqualTo(result.searchId());
        assertThat(calls).hasValue(7);verify(history,times(1)).save(any());
        assertThat(service.results(result.searchId(),new SearchQuery(1,1)).items()).isEmpty();
    }
    @Test void allFailuresRemainInCriteriaHistoryAndAreNotCached(){
        var provider=mock(FlightSearchProvider.class);when(provider.search(any())).thenThrow(new SearchProviderFailure(SearchProviderFailure.Reason.DISABLED));
        var service=service(provider);
        for(int i=0;i<2;i++)assertThatThrownBy(()->service.search(criteria(0),new SearchQuery(0,20))).isInstanceOf(SearchException.class);
        verify(history,times(2)).save(any());verify(provider,times(2)).search(any());
    }
    @Test void limitsOffersAndStopsAtDeadline(){
        var service=service(q->{
            clock.set(clock.instant().plusSeconds(46));
            var offer=SearchDomainTest.offer("a","USD",100,9,4,BagAvailability.INCLUDED);
            return new FlightSearchProvider.Batch(List.of(offer,offer,offer),List.of());
        });
        var result=service.search(criteria(3),new SearchQuery(0,20));
        assertThat(result.items()).hasSize(2);assertThat(result.partial()).isTrue();
        assertThat(result.variants().get(0).warnings()).contains("RESULT_LIMIT");
        assertThat(result.variants().get(1).warnings()).contains("SEARCH_DEADLINE");
    }
    @Test void removesExpiredOffersWithoutRerankingOrPersistingOffers(){
        properties.setResultTtlSeconds(300);
        var provider=(FlightSearchProvider)q->new FlightSearchProvider.Batch(List.of(SearchDomainTest.offer("a","USD",100,9,4,BagAvailability.INCLUDED)),List.of());
        var service=new SearchService(mapper,airports,List.of(provider),new CaffeineSearchSnapshots(properties,clock),history,ranking,clock,new SearchService.Settings(Duration.ofSeconds(300),Duration.ofSeconds(45),100));
        var result=service.search(criteria(0),new SearchQuery(0,20));clock.set(clock.instant().plusSeconds(181));
        var page=service.results(result.searchId(),new SearchQuery(0,20));
        assertThat(page.items()).isEmpty();assertThat(page.partial()).isTrue();assertThat(page.warnings()).contains("EXPIRED_OFFERS_REMOVED");
        clock.set(clock.instant().plusSeconds(120));
        assertThatThrownBy(()->service.results(result.searchId(),new SearchQuery(0,20))).isInstanceOf(SearchException.class);
    }
    @Test void invalidCriteriaNeverCallsProviderOrHistory(){
        var provider=mock(FlightSearchProvider.class);var service=service(provider);
        assertThatThrownBy(()->service.search(null,new SearchQuery(0,20))).isInstanceOf(SearchException.class);
        var bad=new CriteriaData("AAA","AAB",LocalDate.of(2025,1,1),null,0,1,List.of(),"ECONOMY",null);
        assertThatThrownBy(()->service.search(bad,new SearchQuery(0,20))).isInstanceOf(SearchException.class);
        verifyNoInteractions(provider,history);
    }
    @Test void concurrentIdenticalRequestsLoadOnceWhileDifferentSearchIsRejectedAtCapacity() throws Exception {
        properties.setConcurrentSearches(1);
        var cache=new CaffeineSearchSnapshots(properties,clock);var c=SearchDomainTest.criteria(0,null,null);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);AtomicInteger loads=new AtomicInteger();
        var snapshot=new SearchSnapshots.Snapshot(UUID.randomUUID(),clock.instant(),clock.instant().plusSeconds(180),List.of(),List.of(),List.of());
        var executor=Executors.newFixedThreadPool(2);
        try{
            var first=executor.submit(()->cache.load(c,()->{
                loads.incrementAndGet();entered.countDown();
                try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Test timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}
                return snapshot;
            }));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->cache.load(SearchDomainTest.criteria(1,null,null),()->snapshot)).isInstanceOf(SearchException.class);
            var same=executor.submit(()->cache.load(c,()->{loads.incrementAndGet();return snapshot;}));
            release.countDown();
            assertThat(first.get(5,TimeUnit.SECONDS).cached()).isFalse();assertThat(same.get(5,TimeUnit.SECONDS).cached()).isTrue();
            assertThat(loads).hasValue(1);assertThat(cache.find(snapshot.id())).contains(snapshot);
        }finally{release.countDown();executor.shutdownNow();}
        clock.set(clock.instant().plusSeconds(181));
        assertThat(cache.find(snapshot.id())).isEmpty();
        assertThat(cache.load(c,()->new SearchSnapshots.Snapshot(UUID.randomUUID(),clock.instant(),clock.instant().plusSeconds(180),List.of(),List.of(),List.of())).cached()).isFalse();
    }
}
