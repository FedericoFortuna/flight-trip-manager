package com.flighttripmanager.flightsearch.api.mapper;
import org.mapstruct.*;
import com.flighttripmanager.flightsearch.application.contract.*;
import com.flighttripmanager.flightsearch.api.dto.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface SearchApiMapper {
    CriteriaData command(CriteriaDto request);
    SearchResultDto response(SearchResult result);
    HistoryDto history(CriteriaHistory history);
    default SearchPageDto<HistoryDto> response(SearchPage<CriteriaHistory> page){
        return new SearchPageDto<>(page.items().stream().map(this::history).toList(),page.page(),page.size(),page.totalElements(),page.totalPages());
    }
}
