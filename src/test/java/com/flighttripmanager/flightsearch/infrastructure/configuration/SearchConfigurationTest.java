package com.flighttripmanager.flightsearch.infrastructure.configuration;
import java.time.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flighttripmanager.flightsearch.application.port.out.*;
import com.flighttripmanager.flightsearch.infrastructure.providers.*;
import org.junit.jupiter.api.Test;
import testsupport.TripsClockConfiguration.MutableClock;
import static org.assertj.core.api.Assertions.*;
class SearchConfigurationTest {
    @Test void providerModesAreExplicitAndCredentialsNeverAppearInToString(){
        var p=new SearchProperties();var config=new SearchConfiguration();
        SearchAirports airports=iata->new SearchAirports.Airport(iata,ZoneOffset.UTC);
        var clock=Clock.systemUTC();var json=new ObjectMapper();
        assertThatThrownBy(()->config.searchProvider(p,airports,clock,json).search(null)).isInstanceOf(SearchProviderFailure.class);
        p.setMode(SearchProperties.Mode.MOCK);assertThat(config.searchProvider(p,airports,clock,json)).isInstanceOf(MockSearchProvider.class);
        p.setMode(SearchProperties.Mode.DUFFEL);assertThatThrownBy(()->config.searchProvider(p,airports,clock,json)).isInstanceOf(IllegalStateException.class);
        p.setDuffelToken("secret-fixture");assertThat(p.toString()).doesNotContain("secret-fixture");
        assertThat(config.searchProvider(p,airports,clock,json)).isInstanceOf(DuffelSearchProvider.class);
        p.setRequestTimeoutMs(5000);assertThatThrownBy(()->config.searchProvider(p,airports,clock,json)).isInstanceOf(IllegalStateException.class);
        assertThat(config.searchSettings(p).maxOffersPerVariant()).isEqualTo(100);
        assertThat(config.searchRanking(p).rank(java.util.List.of())).isEmpty();
    }
    @Test void guardsRecoverAfterCooldownAndQuotaWindow(){
        var p=new SearchProperties();p.setRequestsPerWindow(1);p.setCircuitThreshold(1);
        var clock=new MutableClock();var guard=new ProviderCallGuard(p,clock);
        guard.acquire();assertThatThrownBy(guard::acquire).isInstanceOf(SearchProviderFailure.class);
        clock.set(clock.instant().plusSeconds(60));guard.acquire();
        guard.failure();assertThatThrownBy(guard::acquire).isInstanceOf(SearchProviderFailure.class);
        clock.set(clock.instant().plusSeconds(60));guard.acquire();guard.success();
        guard.authenticationFailed();assertThatThrownBy(guard::acquire).isInstanceOf(SearchProviderFailure.class);
        clock.set(clock.instant().plusSeconds(60));guard.acquire();
        guard.limitedUntil(clock.instant().plusSeconds(60));assertThatThrownBy(guard::acquire).isInstanceOf(SearchProviderFailure.class);
        clock.set(clock.instant().plusSeconds(60));guard.acquire();
    }
}
