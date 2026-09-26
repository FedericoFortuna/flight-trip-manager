package com.flighttripmanager.trips.infrastructure.persistence.entity;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name = "passengers", schema = "trips")
@Getter @Setter @NoArgsConstructor
public class PassengerJpaEntity {
    @Id private UUID id;
    @Column(name = "trip_id", nullable = false) private UUID tripId;
    @Column(name = "first_name", nullable = false, length = 100) private String firstName;
    @Column(name = "last_name", nullable = false, length = 100) private String lastName;
    @Column(length = 2000) private String notes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
