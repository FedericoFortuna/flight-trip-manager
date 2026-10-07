package com.flighttripmanager.flightsearch.infrastructure.persistence.repository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.flighttripmanager.flightsearch.infrastructure.persistence.entity.SearchHistoryJpaEntity;
public interface SearchHistoryJpaRepository extends JpaRepository<SearchHistoryJpaEntity,UUID> {}
