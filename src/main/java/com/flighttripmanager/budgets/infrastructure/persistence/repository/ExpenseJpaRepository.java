package com.flighttripmanager.budgets.infrastructure.persistence.repository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.flighttripmanager.budgets.infrastructure.persistence.entity.ExpenseJpaEntity;
public interface ExpenseJpaRepository extends JpaRepository<ExpenseJpaEntity,UUID>{
    boolean existsByTripIdAndReferenceKeyAndIdNot(UUID tripId,String referenceKey,UUID id);
    Optional<ExpenseJpaEntity> findByTripIdAndId(UUID tripId,UUID id);
    Page<ExpenseJpaEntity> findByTripId(UUID tripId,Pageable page);
    void deleteByTripIdAndId(UUID tripId,UUID id);
}
