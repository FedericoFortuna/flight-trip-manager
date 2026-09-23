package com.flighttripmanager.catalog;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.util.UUID;
import com.flighttripmanager.catalog.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CatalogDomainTest {
    private final UUID id = UUID.randomUUID();

    @Test void validCatalogValuesKeepUnknownDataAbsent() {
        var airport = airport(null, null, "UTC");
        assertThat(airport.latitude()).isNull();
        assertThat(airport.longitude()).isNull();
        assertThat(airport.icaoCode()).isNull();
        assertThat(airport.lastSyncedAt()).isNull();
        assertThat(new Airline(id, "4U", null, "Example Airline", "AR", true, null).iataCode()).isEqualTo("4U");
        for (var type : LocationType.values()) {
            assertThat(new Location(id, type, "Name", "City", "AR").type()).isEqualTo(type);
        }
        assertThat(airport(new BigDecimal("-90"), new BigDecimal("-180"), "UTC").latitude()).isEqualByComparingTo("-90");
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", " name", "name ", "\t"})
    void namesMustBePresentAndCanonical(String name) {
        assertThatThrownBy(() -> new Airline(id, "AR", null, name, "AR", true, null))
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    @Test void enforcesTextLengthsRequiredIdentityAndCodes() {
        assertThatThrownBy(() -> new Airline(id, "AR", null, "n".repeat(201), "AR", true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Location(id, LocationType.CITY, "Name", "c".repeat(121), "AR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Location(null, LocationType.CITY, "Name", "City", "AR")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Location(id, null, "Name", "City", "AR")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Airline(id, "ar", null, "Name", "AR", true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Airline(id, null, null, "Name", "AR", true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Airline(id, "AR", "ab", "Name", "AR", true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Airline(id, "AR", null, "Name", "arg", true, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsPartialOrOutOfRangeCoordinatesAndUnknownTimezones() {
        assertThatThrownBy(() -> airport(BigDecimal.ZERO, null, "UTC")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> airport(null, BigDecimal.ZERO, "UTC")).isInstanceOf(IllegalArgumentException.class);
        for (String latitude : new String[] {"90.000001", "-90.000001"}) {
            assertThatThrownBy(() -> airport(new BigDecimal(latitude), BigDecimal.ZERO, "UTC")).isInstanceOf(IllegalArgumentException.class);
        }
        for (String longitude : new String[] {"180.000001", "-180.000001"}) {
            assertThatThrownBy(() -> airport(BigDecimal.ZERO, new BigDecimal(longitude), "UTC")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> airport(null, null, "Invalid/Zone")).isInstanceOf(DateTimeException.class);
    }

    private Airport airport(BigDecimal latitude, BigDecimal longitude, String zone) {
        return new Airport(id, "AAA", null, "Example Airport", "City", "AR", latitude, longitude, zone, true, null);
    }
}
