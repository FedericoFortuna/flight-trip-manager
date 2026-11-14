package com.flighttripmanager.budgets.infrastructure.persistence.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.flighttripmanager.budgets.infrastructure.persistence.entity.BudgetLimitJpaEntity;
public interface BudgetLimitJpaRepository extends JpaRepository<BudgetLimitJpaEntity,UUID>{
    List<BudgetLimitJpaEntity> findByTripIdAndScope(UUID tripId,String scope);
    Optional<BudgetLimitJpaEntity> findByTripIdAndLegId(UUID tripId,UUID legId);
    void deleteByTripIdAndScope(UUID tripId,String scope);
}
