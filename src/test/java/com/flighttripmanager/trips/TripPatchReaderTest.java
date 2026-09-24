package com.flighttripmanager.trips;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flighttripmanager.trips.api.mapper.TripPatchReader;
import com.flighttripmanager.trips.application.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TripPatchReaderTest {
    private final TripPatchReader reader = new TripPatchReader(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test void omissionAndNullAreDistinctAndMoneyIsExact() {
        var patch = reader.trip("{\"version\":0,\"totalBudgetUsd\":12345678901234567.89,\"manualStatusOverride\":null}");
        assertThat(patch.name().present()).isFalse();
        assertThat(patch.name().apply("Previous")).isEqualTo("Previous");
        assertThat(patch.manualStatusOverride().present()).isTrue();
        assertThat(patch.manualStatusOverride().value()).isNull();
        assertThat(patch.totalBudgetUsd().value()).isEqualByComparingTo("12345678901234567.89");
    }
    @ParameterizedTest @ValueSource(strings = {
            "null", "[]", "{}", "{", "{\"version\":0}", "{\"name\":\"Trip\",\"version\":1.1}",
            "{\"version\":0,\"unknown\":1}", "{\"version\":0,\"name\":12}",
            "{\"version\":0,\"totalBudgetUsd\":\"1.20\"}", "{\"version\":0,\"startDate\":\"invalid\"}",
            "{\"version\":0,\"name\":\"one\",\"name\":\"two\"}",
            "{\"version\":99999999999999999999999,\"name\":\"Trip\"}"})
    void malformedOrAmbiguousTripPatchesAreRejected(String body) {
        assertThatThrownBy(() -> reader.trip(body)).isInstanceOf(TripsException.class);
    }
    @Test void legPatchesPreservePresenceAndRejectMalformedReferences() {
        var patch = reader.leg("{\"version\":1,\"departureDateTime\":null,\"departureDate\":\"2026-06-01\",\"status\":\"PLANNED\"}");
        assertThat(patch.departureDateTime().present()).isTrue();
        assertThat(patch.departureDateTime().value()).isNull();
        assertThat(patch.arrivalDateTime().present()).isFalse();
        assertThat(patch.status().value()).isEqualTo(LegStatusValue.PLANNED);
        assertThatThrownBy(() -> reader.leg("{\"version\":0,\"origin\":\"bad\"}")).isInstanceOf(TripsException.class);
        assertThatThrownBy(() -> reader.leg("{\"version\":0,\"origin\":{\"kind\":\"CITY\",\"id\":\"bad\"}}")).isInstanceOf(TripsException.class);
    }
}
