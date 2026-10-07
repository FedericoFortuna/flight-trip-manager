package com.flighttripmanager.flightsearch;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.flighttripmanager.flightsearch.domain.model.*;
import static org.assertj.core.api.Assertions.*;
class SavedOptionDomainTest {
    final Instant now=Instant.parse("2026-06-01T00:00:00Z");
    SavedFlightOption option(SearchOffer offer){
        return new SavedFlightOption(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,
            SearchDomainTest.criteria(0,null,null),offer,true,null,now,now);
    }
    @Test void preservesNonUsdMoneyAndUnknownBreakdownWithoutInventingCosts(){
        var offer=SearchDomainTest.offer("eur","EUR",150,9,4,BagAvailability.INCLUDED);
        var p=FlightPriceSnapshot.observed(UUID.randomUUID(),UUID.randomUUID(),offer);
        assertThat(p.originalAmount()).isEqualByComparingTo("150");assertThat(p.originalCurrency()).isEqualTo("EUR");
        assertThat(p.amountUsd()).isNull();assertThat(p.basePrice()).isNull();assertThat(p.baggagePrice()).isNull();assertThat(p.seatPrice()).isNull();
        assertThat(p.totalPrice()).isEqualTo(p.originalAmount());assertThat(p.missingCosts()).containsExactly("SEAT_SELECTION","AIRPORT_TRANSFER");
    }
    @Test void priceChangeAndNewOfferIdentityAreAllowedButDifferentProviderOrProductIsNot(){
        var original=SearchDomainTest.offer("a","USD",100,9,4,BagAvailability.INCLUDED);var saved=option(original);
        var repriced=SearchDomainTest.offer("b","EUR",120,9,4,BagAvailability.INCLUDED);
        assertThat(saved.sameProduct(saved.criteria(),repriced)).isTrue();
        var otherProvider=new SearchOffer("DUFFEL",repriced.providerOfferId(),"ZZ",false,true,repriced.amount(),"EUR",repriced.checkedBag(),repriced.observedAt(),repriced.expiresAt(),repriced.slices());
        assertThat(saved.sameProduct(saved.criteria(),otherProvider)).isFalse();
        assertThat(saved.sameProduct(saved.criteria(),SearchDomainTest.offer("b","USD",100,12,4,BagAvailability.INCLUDED))).isFalse();
        assertThat(saved.sameProduct(saved.criteria(),SearchDomainTest.offer("b","USD",100,9,4,BagAvailability.UNKNOWN))).isFalse();
        var family=new SearchCriteria("AAA","AAB",SearchDomainTest.DATE,null,0,1,List.of(8),null,null);
        assertThat(saved.sameProduct(family,original)).isFalse();
        var business=new SearchCriteria("AAA","AAB",SearchDomainTest.DATE,null,0,1,List.of(),Cabin.BUSINESS,null);
        assertThat(saved.sameProduct(business,original)).isFalse();
    }
    @Test void deactivationPreservesHistoryAndNeverMovesTimestampBackwards(){
        var saved=option(SearchDomainTest.offer("a","USD",100,9,4,BagAvailability.INCLUDED));
        var closed=saved.deactivate("LEG_CLOSED",now.minusSeconds(1));
        assertThat(closed.active()).isFalse();assertThat(closed.initialOffer()).isSameAs(saved.initialOffer());
        assertThat(closed.updatedAt()).isEqualTo(now);assertThat(closed.createdAt()).isEqualTo(now);
    }
    @Test void rejectsImpossibleSlotsAndInvalidMoney(){
        var offer=SearchDomainTest.offer("a","USD",100,9,4,BagAvailability.INCLUDED);
        for(int slot:new int[]{0,4})assertThatThrownBy(()->new SavedFlightOption(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),slot,
            SearchDomainTest.criteria(0,null,null),offer,true,null,now,now)).isInstanceOf(SearchRuleViolation.class);
        assertThatThrownBy(()->new FlightPriceSnapshot(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"id",null,null,null,
            BigDecimal.TEN,"USD",BigDecimal.TEN,null,"MOCK",now,true,true,List.of())).isInstanceOf(SearchRuleViolation.class);
        assertThatThrownBy(()->new FlightPriceSnapshot(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"id",null,null,null,
            new BigDecimal("-1"),"EUR",new BigDecimal("-1"),null,"MOCK",now,true,true,List.of())).isInstanceOf(SearchRuleViolation.class);
    }
}
