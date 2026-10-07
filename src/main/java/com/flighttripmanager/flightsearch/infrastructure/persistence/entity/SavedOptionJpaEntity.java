package com.flighttripmanager.flightsearch.infrastructure.persistence.entity;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
@Entity @Table(schema="flightsearch",name="saved_flight_options")
@Getter @Setter @NoArgsConstructor
public class SavedOptionJpaEntity {
    @Id private UUID id;
    private UUID tripId;
    private UUID legId;
    private String transportType;
    private int slot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private String criteria;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private String initialOffer;
    private boolean active;
    private String inactiveReason;
    private Instant createdAt;
    private Instant updatedAt;
}
