package com.flighttripmanager.flightsearch.application.port.out;
import com.flighttripmanager.flightsearch.application.contract.*;
public interface SearchHistoryStore {
    java.util.Optional<CriteriaHistory> find(java.util.UUID id);
    void save(CriteriaHistory entry);
    SearchPage<CriteriaHistory> list(SearchQuery page);
}
