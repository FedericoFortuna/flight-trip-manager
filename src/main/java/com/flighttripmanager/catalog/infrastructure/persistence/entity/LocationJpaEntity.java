package com.flighttripmanager.catalog.infrastructure.persistence.entity;

import java.util.UUID;
import com.flighttripmanager.catalog.domain.model.LocationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "locations", schema = "catalog")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LocationJpaEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationType type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 2)
    private String country;
}
