package com.flighttripmanager.flightsearch.application.port.in;
import java.util.UUID;
import com.flighttripmanager.flightsearch.application.contract.*;
public interface FlightSearch {
    SearchResult search(CriteriaData criteria,SearchQuery page);
    SearchResult results(UUID id,SearchQuery page);
    SearchPage<CriteriaHistory> history(SearchQuery page);
}
