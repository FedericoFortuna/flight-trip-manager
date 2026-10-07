package com.flighttripmanager.flightsearch.infrastructure.configuration;
import java.time.*;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.flighttripmanager.flightsearch.application.port.out.*;
import com.flighttripmanager.flightsearch.application.usecase.SearchService;
import com.flighttripmanager.flightsearch.domain.model.SearchRanking;
import com.flighttripmanager.flightsearch.infrastructure.providers.*;
@Configuration @EnableConfigurationProperties(SearchProperties.class)
public class SearchConfiguration {
    @Bean SearchRanking searchRanking(SearchProperties p){
        return new SearchRanking(new SearchRanking.Weights(p.getPriceWeight(),p.getDurationWeight(),p.getStopsWeight(),p.getScheduleWeight(),p.getBaggageWeight(),p.getMissingCostsWeight()));
    }
    @Bean SearchService.Settings searchSettings(SearchProperties p){
        return new SearchService.Settings(Duration.ofSeconds(p.getResultTtlSeconds()),Duration.ofSeconds(p.getDeadlineSeconds()),p.getMaxOffersPerVariant());
    }
    @Bean FlightSearchProvider searchProvider(SearchProperties p,SearchAirports airports,Clock clock,ObjectMapper json){
        return switch(p.getMode()){
            case DISABLED -> query->{throw new SearchProviderFailure(SearchProviderFailure.Reason.DISABLED);};
            case MOCK -> new MockSearchProvider(airports,clock);
            case DUFFEL -> {
                if(p.getDuffelToken().isBlank()||p.getDuffelToken().contains("\n")||p.getDuffelToken().contains("\r"))
                    throw new IllegalStateException("Duffel test token must be configured through the environment");
                if(p.getRequestTimeoutMs()<=p.getSupplierTimeoutMs())throw new IllegalStateException("Request timeout must exceed supplier timeout");
                URI uri=URI.create("https://api.duffel.com/air/offer_requests?return_offers=true&supplier_timeout="+p.getSupplierTimeoutMs());
                yield new DuffelSearchProvider(new DuffelHttp(uri,p,clock),new DuffelOfferReader(json,clock),json);
            }
        };
    }
}
