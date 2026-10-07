package com.flighttripmanager.flightsearch.application.port.out;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;

import java.util.function.Supplier;
import com.flighttripmanager.flightsearch.domain.model.*;
import com.flighttripmanager.flightsearch.application.contract.VariantData;
public interface SearchSnapshots {
    record Snapshot(UUID id,Instant createdAt,Instant expiresAt,List<SearchRanking.Ranked> offers,List<VariantData> variants,List<String> warnings){}
    record Loaded(Snapshot snapshot,boolean cached){}
    Loaded load(SearchCriteria criteria,Supplier<Snapshot> supplier);
    Optional<Snapshot> find(UUID id);
}
