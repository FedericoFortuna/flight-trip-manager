package com.flighttripmanager.flightsearch;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.flighttripmanager.flightsearch.domain.model.*;
import static org.assertj.core.api.Assertions.*;
class SearchDomainTest {
    static final LocalDate DATE=LocalDate.of(2026,10,1);
    static SearchCriteria criteria(int flex,LocalDate returning,SearchFilters filters){
        return new SearchCriteria(" aaa ","aab",DATE,returning,flex,1,List.of(),null,filters);
    }
    static SearchOffer offer(String id,String currency,int price,int hour,int hours,BagAvailability bag){
        var departure=DATE.atTime(hour,0);var arrival=departure.plusHours(hours);
        var segment=new SearchSegment("AAA","AAB","ZZ",null,"ZZ100",departure,arrival,
            departure.toInstant(ZoneOffset.UTC),arrival.toInstant(ZoneOffset.UTC),List.of());
        return new SearchOffer("MOCK",id,"ZZ",true,true,BigDecimal.valueOf(price),currency,bag,
            Instant.parse("2026-06-01T00:00:00Z"),Instant.parse("2026-06-01T00:03:00Z"),List.of(new SearchSlice(List.of(segment))));
    }
    @Test void flexibleRoundTripPreservesStayAndUsesSevenDates(){
        var c=criteria(3,DATE.plusDays(9),null);
        assertThat(c.origin()).isEqualTo("AAA");assertThat(c.cabin()).isEqualTo(Cabin.ECONOMY);
        assertThat(c.variants()).extracting(SearchCriteria.Dates::offset).containsExactly(0,-1,1,-2,2,-3,3);
        assertThat(c.variants()).allSatisfy(d->assertThat(ChronoUnit.DAYS.between(d.departure(),d.returning())).isEqualTo(9));
        assertThat(criteria(0,null,null).variants()).hasSize(1).allSatisfy(d->assertThat(d.returning()).isNull());
    }
    @ParameterizedTest @ValueSource(ints={-1,4,99}) void rejectsExcessiveFlex(int flex){
        assertThatThrownBy(()->criteria(flex,null,null)).isInstanceOf(SearchRuleViolation.class);
    }
    @Test void rejectsInvalidPassengersAndDates(){
        assertThatThrownBy(()->criteria(0,DATE.minusDays(1),null)).isInstanceOf(SearchRuleViolation.class);
        for(var ages:List.of(List.of(-1),List.of(18),List.of(0,1),Collections.<Integer>singletonList(null)))
            assertThatThrownBy(()->new SearchCriteria("AAA","AAB",DATE,null,0,1,ages,null,null)).isInstanceOf(SearchRuleViolation.class);
        assertThatThrownBy(()->new SearchCriteria("AAA","AAA",DATE,null,0,1,null,null,null)).isInstanceOf(SearchRuleViolation.class);
        assertThatThrownBy(()->new SearchCriteria("AAA","AAB",DATE,null,0,0,null,null,null)).isInstanceOf(SearchRuleViolation.class);
        assertThatThrownBy(()->new SearchCriteria("AAA","AAB",DATE,null,0,10,null,null,null)).isInstanceOf(SearchRuleViolation.class);
        assertThatThrownBy(()->new SearchCriteria("AAA","AAB",DATE,null,0,Integer.MAX_VALUE,List.of(5),null,null)).isInstanceOf(SearchRuleViolation.class);
        assertThat(new SearchCriteria("AAA","AAB",DATE,null,0,1,List.of(12,4),null,null).childAges()).containsExactly(4,12);
    }
    @Test void overnightWindowAndExplicitBaggageFilter(){
        var filters=new SearchFilters(Set.of("zz"),0,LocalTime.of(22,0),LocalTime.of(5,0),300,true,Set.of("CCC"));
        assertThat(filters.acceptsTime(LocalTime.MIDNIGHT)).isTrue();assertThat(filters.acceptsTime(LocalTime.NOON)).isFalse();
        var c=criteria(0,null,filters);
        assertThat(offer("1","USD",100,23,4,BagAvailability.INCLUDED).matches(c,c.variants().get(0))).isTrue();
        assertThat(offer("1","USD",100,23,4,BagAvailability.UNKNOWN).matches(c,c.variants().get(0))).isFalse();
        assertThat(offer("1","USD",100,23,6,BagAvailability.INCLUDED).matches(c,c.variants().get(0))).isFalse();
        assertThat(offer("1","USD",100,12,4,BagAvailability.INCLUDED).matches(c,c.variants().get(0))).isFalse();
    }
    @Test void filtersAirlinesAndExcludedAirports(){
        var o=offer("1","EUR",100,9,4,BagAvailability.INCLUDED);
        for(var f:List.of(new SearchFilters(Set.of("A1"),null,null,null,null,false,null),
                new SearchFilters(null,null,null,null,null,false,Set.of("AAB")))){
            var c=criteria(0,null,f);assertThat(o.matches(c,c.variants().get(0))).isFalse();
        }
        assertThat(o.amountUsd()).isNull();assertThat(o.missingCosts()).containsExactly("SEAT_SELECTION","AIRPORT_TRANSFER");
        assertThat(offer("u","USD",100,9,4,BagAvailability.UNKNOWN).missingCosts()).contains("CHECKED_BAGGAGE");
        assertThat(offer("u","USD",100,9,4,BagAvailability.UNKNOWN).amountUsd()).isEqualByComparingTo("100");
    }
    @Test void validatesFilterBoundsAndPairs(){
        assertThatThrownBy(()->new SearchFilters(null,4,null,null,null,false,null)).isInstanceOf(SearchRuleViolation.class);
        assertThatThrownBy(()->new SearchFilters(null,null,LocalTime.NOON,null,null,false,null)).isInstanceOf(SearchRuleViolation.class);
        assertThatThrownBy(()->new SearchFilters(null,null,null,null,0,false,null)).isInstanceOf(SearchRuleViolation.class);
        assertThatThrownBy(()->new SearchFilters(Set.of("XXX"),null,null,null,null,false,null)).isInstanceOf(SearchRuleViolation.class);
    }
    @Test void rankingIsDeterministicWithinCurrencyAndExposesPenaltyBreakdown(){
        var weights=new SearchRanking.Weights(new BigDecimal(40),new BigDecimal(25),new BigDecimal(15),new BigDecimal(10),new BigDecimal(10),new BigDecimal(5));
        var rank=new SearchRanking(weights);
        var a=offer("a","USD",100,9,4,BagAvailability.INCLUDED);
        var b=offer("b","USD",200,23,6,BagAvailability.UNKNOWN);
        var eur=offer("e","EUR",1,9,4,BagAvailability.INCLUDED);
        assertThat(rank.rank(List.of(b,eur,a))).extracting(r->r.offer().providerOfferId()).containsExactly("a","b","e");
        assertThat(rank.rank(List.of(a,b,eur))).isEqualTo(rank.rank(List.of(eur,b,a)));
        var result=rank.rank(List.of(a,b)).get(1);
        assertThat(result.score()).isEqualByComparingTo("100");
        assertThat(result.penalties()).containsKeys("price","duration","stops","schedule","baggage","missingCosts");
        assertThat(rank.rank(List.of(a,a))).hasSize(2); // Never collapse offers from separate sources.
        assertThat(rank.rank(List.of())).isEmpty();
        assertThatThrownBy(()->new SearchRanking.Weights(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO)).isInstanceOf(SearchRuleViolation.class);
    }
}
