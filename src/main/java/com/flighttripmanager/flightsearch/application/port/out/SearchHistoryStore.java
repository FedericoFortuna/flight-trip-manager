package com.flighttripmanager.flightsearch.application.port.out;
import com.flighttripmanager.flightsearch.application.contract.*;
public interface SearchHistoryStore {
    void save(CriteriaHistory entry);
    SearchPage<CriteriaHistory> list(SearchQuery page);
}
