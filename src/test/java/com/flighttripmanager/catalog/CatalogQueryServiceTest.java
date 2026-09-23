package com.flighttripmanager.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.flighttripmanager.catalog.application.contract.*;
import com.flighttripmanager.catalog.application.mapper.CatalogViewMapper;
import com.flighttripmanager.catalog.application.port.out.CatalogStore;
import com.flighttripmanager.catalog.application.usecase.CatalogQueryService;
import com.flighttripmanager.catalog.domain.model.Airline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogQueryServiceTest {
    private final CatalogStore store = mock(CatalogStore.class);
    private final CatalogQueryService service = new CatalogQueryService(store, Mappers.getMapper(CatalogViewMapper.class));

    @Test void normalizesCodesBeforeLookingUpLocalStorage() {
        var airline = new Airline(UUID.randomUUID(), "A1", "AAA", "Example", "AR", true, null);
        when(store.airline("A1")).thenReturn(Optional.of(airline));
        assertThat(service.airline("a1").id()).isEqualTo(airline.id());
        verify(store).airline("A1");
        verifyNoMoreInteractions(store);
    }

    @Test void missingRecordsHaveDistinctApplicationErrors() {
        when(store.airport("AAA")).thenReturn(Optional.empty());
        when(store.airline("AA")).thenReturn(Optional.empty());
        UUID id = UUID.randomUUID();
        when(store.location(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.airport("aaa")).isInstanceOfSatisfying(CatalogNotFoundException.class,
                error -> assertThat(error.code()).isEqualTo("AIRPORT_NOT_FOUND"));
        assertThatThrownBy(() -> service.airline("aa")).isInstanceOfSatisfying(CatalogNotFoundException.class,
                error -> assertThat(error.code()).isEqualTo("AIRLINE_NOT_FOUND"));
        assertThatThrownBy(() -> service.location(id)).isInstanceOfSatisfying(CatalogNotFoundException.class,
                error -> assertThat(error.code()).isEqualTo("LOCATION_NOT_FOUND"));
    }

    @Test void invalidCodesNeverReachStorage() {
        assertThatThrownBy(() -> service.airport("A1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.airline("AAA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.location(null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(store);
    }

    @ParameterizedTest @CsvSource({"-1,20", "1000001,20", "0,0", "0,101"})
    void paginationIsValidatedForNonHttpConsumers(int page, int size) {
        assertThatThrownBy(() -> new CatalogQuery("", page, size)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void queryNormalizesTextAndBoundsItsLength() {
        assertThat(new CatalogQuery(null, 0, 20).q()).isEmpty();
        assertThat(new CatalogQuery("  ABC  ", 0, 20).q()).isEqualTo("abc");
        assertThat(new CatalogQuery("%_", 1_000_000, 100).q()).isEqualTo("%_");
        assertThatThrownBy(() -> new CatalogQuery("x".repeat(101), 0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void pagesAreImmutableAndCalculatePartialLastPages() {
        var source = new ArrayList<>(List.of("one"));
        var page = new CatalogPage<>(source, 0, 20, 21);
        source.clear();
        assertThat(page.items()).containsExactly("one");
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(new CatalogPage<>(List.of(), 0, 20, 0).totalPages()).isZero();
        assertThat(new CatalogPage<>(List.of(), 0, 20, 20).totalPages()).isEqualTo(1);
        assertThatThrownBy(() -> new CatalogPage<>(List.of(), -1, 20, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogPage<>(List.of(), 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogPage<>(List.of(), 0, 20, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
