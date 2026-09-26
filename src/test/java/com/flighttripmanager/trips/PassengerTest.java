package com.flighttripmanager.trips;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.flighttripmanager.trips.domain.model.*;
import static org.assertj.core.api.Assertions.*;

class PassengerTest {
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");
    @Test void preservesInternationalNamesAndOptionalNotes() {
        var passenger = passenger("  María-José  ", " O’Connor 李 ", "Line 1\nLine 2\t!");
        assertThat(passenger.firstName()).isEqualTo("María-José");
        assertThat(passenger.lastName()).isEqualTo("O’Connor 李");
        assertThat(passenger.notes()).isEqualTo("Line 1\nLine 2\t!");
        assertThat(passenger("A", "B", null).notes()).isNull();
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t", "\u2003", "A\nB"})
    void rejectsMissingBlankOrControlCharacterNames(String name) {
        assertThatThrownBy(() -> passenger(name, "B", null)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> passenger("A", name, null)).isInstanceOf(TripRuleViolation.class);
    }
    @Test void validatesBoundsWithoutTruncation() {
        assertThat(passenger("A".repeat(100), "B".repeat(100), "x".repeat(2000)).notes()).hasSize(2000);
        assertThatThrownBy(() -> passenger("A".repeat(101), "B", null)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> passenger("A", "B".repeat(101), null)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> passenger("A", "B", "x".repeat(2001))).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> passenger("A", "B", "bad\u0000")).isInstanceOf(TripRuleViolation.class);
    }
    @Test void requiresIdentityAndConsistentTimestamps() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new Passenger(null, id, "A", "B", null, NOW, NOW)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> new Passenger(id, null, "A", "B", null, NOW, NOW)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> new Passenger(id, id, "A", "B", null, null, NOW)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> new Passenger(id, id, "A", "B", null, NOW, null)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> new Passenger(id, id, "A", "B", null, NOW, NOW.minusSeconds(1))).isInstanceOf(TripRuleViolation.class);
    }
    private Passenger passenger(String first, String last, String notes) {
        return new Passenger(UUID.randomUUID(), UUID.randomUUID(), first, last, notes, NOW, NOW);
    }
}
