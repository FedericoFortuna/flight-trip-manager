package com.flighttripmanager.flightsearch.api.mapper;
import org.mapstruct.*;
import com.flighttripmanager.flightsearch.application.contract.*;
import com.flighttripmanager.flightsearch.api.dto.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface SavedOptionApiMapper {
    SaveOption command(SaveOptionRequest request);
    SavedOptionDto response(SavedOptionView view);
    PriceSnapshotDto response(PriceSnapshotData snapshot);
    PriceObservationDto response(PriceObservation observation);
    default SearchPageDto<SavedOptionDto> options(SearchPage<SavedOptionView> page){
        return new SearchPageDto<>(page.items().stream().map(this::response).toList(),page.page(),page.size(),page.totalElements(),page.totalPages());
    }
    default SearchPageDto<PriceSnapshotDto> prices(SearchPage<PriceSnapshotData> page){
        return new SearchPageDto<>(page.items().stream().map(this::response).toList(),page.page(),page.size(),page.totalElements(),page.totalPages());
    }
}
