package com.flighttripmanager.flightsearch.infrastructure.persistence.entity;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
@Entity @Table(name="search_history",schema="flightsearch")
@Getter @Setter @NoArgsConstructor
public class SearchHistoryJpaEntity {
    @Id private UUID id;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private String criteria;
}
