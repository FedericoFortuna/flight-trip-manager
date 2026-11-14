package com.flighttripmanager.budgets.infrastructure.persistence.adapter;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import com.flighttripmanager.budgets.application.contract.*;
import com.flighttripmanager.budgets.application.port.out.BudgetStore;
import com.flighttripmanager.budgets.domain.model.*;
import com.flighttripmanager.budgets.infrastructure.persistence.entity.BudgetLimitJpaEntity;
import com.flighttripmanager.budgets.infrastructure.persistence.repository.*;
import com.flighttripmanager.budgets.infrastructure.persistence.mapper.ExpensePersistenceMapper;
@Component
public class JpaBudgetStore implements BudgetStore {
    private final BudgetLimitJpaRepository limits;private final ExpenseJpaRepository expenses;
    private final ExpensePersistenceMapper mapper;private final EntityManager em;private final JdbcTemplate jdbc;
    public JpaBudgetStore(BudgetLimitJpaRepository limits,ExpenseJpaRepository expenses,ExpensePersistenceMapper mapper,EntityManager em,JdbcTemplate jdbc){
        this.limits=limits;this.expenses=expenses;this.mapper=mapper;this.em=em;this.jdbc=jdbc;
    }
    @Override public Map<TransportCategory,BigDecimal> categories(UUID id){
        return limits.findByTripIdAndScope(id,"CATEGORY").stream().collect(Collectors.toMap(l->TransportCategory.valueOf(l.getCategory()),BudgetLimitJpaEntity::getAmountUsd));
    }
    @Override public Map<UUID,BigDecimal> legLimits(UUID id){
        return limits.findByTripIdAndScope(id,"LEG").stream().collect(Collectors.toMap(BudgetLimitJpaEntity::getLegId,BudgetLimitJpaEntity::getAmountUsd));
    }
    @Override public void replaceCategories(UUID id,Map<TransportCategory,BigDecimal> values){
        limits.deleteByTripIdAndScope(id,"CATEGORY");limits.flush();
        values.forEach((category,amount)->{
            var entity=new BudgetLimitJpaEntity();entity.setId(UUID.randomUUID());entity.setTripId(id);entity.setScope("CATEGORY");
            entity.setCategory(category.name());entity.setAmountUsd(amount);em.persist(entity);
        });em.flush();
    }
    @Override public void setLegLimit(UUID tripId,UUID legId,BigDecimal amount){
        var old=limits.findByTripIdAndLegId(tripId,legId);
        if(amount==null){old.ifPresent(limits::delete);limits.flush();return;}
        var entity=old.orElseGet(()->{var created=new BudgetLimitJpaEntity();created.setId(UUID.randomUUID());created.setTripId(tripId);created.setLegId(legId);created.setScope("LEG");return created;});
        entity.setAmountUsd(amount);limits.saveAndFlush(entity);
    }
    @Override public Optional<TransportExpense> expense(UUID tripId,UUID id){return expenses.findByTripIdAndId(tripId,id).map(mapper::domain);}
    @Override public BudgetPage<TransportExpense> expenses(UUID tripId,BudgetQuery query){
        var page=expenses.findByTripId(tripId,PageRequest.of(query.page(),query.size(),Sort.by(Sort.Order.desc("createdAt"),Sort.Order.asc("id"))));
        return new BudgetPage<>(page.getContent().stream().map(mapper::domain).toList(),page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
    }
    @Override public void save(TransportExpense expense,boolean insert){
        if(expense.referenceKey()!=null&&expenses.existsByTripIdAndReferenceKeyAndIdNot(expense.tripId(),expense.referenceKey(),expense.id()))
            throw new BudgetException(BudgetException.Reason.DUPLICATE_REFERENCE);
        try{var entity=mapper.entity(expense);if(insert){em.persist(entity);em.flush();}else expenses.saveAndFlush(entity);}
        catch(RuntimeException error){
            Throwable cause=error;
            while(cause!=null){
                if(cause instanceof org.hibernate.exception.ConstraintViolationException violation){
                    throw new BudgetException("expense_reference_key_uq".equals(violation.getConstraintName())?
                        BudgetException.Reason.DUPLICATE_REFERENCE:BudgetException.Reason.CONFLICT);
                }
                cause=cause.getCause();
            }
            if(error instanceof DataIntegrityViolationException)throw new BudgetException(BudgetException.Reason.CONFLICT);
            throw error;
        }
    }
    @Override public void delete(UUID tripId,UUID id){expenses.deleteByTripIdAndId(tripId,id);expenses.flush();}
    @Override public List<SpendingAggregate> aggregate(UUID tripId){
        // Aggregate in PostgreSQL; never load an unbounded ledger to calculate totals.
        return jdbc.query("""
            WITH entries AS (
                SELECT category,leg_id,'ESTIMATED' AS kind,estimated_currency AS currency,
                    estimated_amount AS amount,estimated_amount_usd AS usd
                FROM budgets.transport_expenses WHERE trip_id=? AND estimated_amount IS NOT NULL
                UNION ALL
                SELECT category,leg_id,'ACTUAL',actual_currency,actual_amount,actual_amount_usd
                FROM budgets.transport_expenses WHERE trip_id=? AND actual_amount IS NOT NULL
                UNION ALL
                SELECT category,leg_id,'FORECAST',
                    CASE WHEN actual_amount IS NOT NULL THEN actual_currency ELSE estimated_currency END,
                    CASE WHEN actual_amount IS NOT NULL THEN actual_amount ELSE estimated_amount END,
                    CASE WHEN actual_amount IS NOT NULL THEN actual_amount_usd ELSE estimated_amount_usd END
                FROM budgets.transport_expenses WHERE trip_id=?
            )
            SELECT category,leg_id,kind,currency,SUM(amount) AS original_amount,
                COALESCE(SUM(usd),0) AS known_usd,COUNT(*) FILTER (WHERE usd IS NULL) AS missing_usd_count
            FROM entries GROUP BY category,leg_id,kind,currency
            """,(rs,n)->new SpendingAggregate(TransportCategory.valueOf(rs.getString("category")),
                rs.getObject("leg_id",UUID.class),rs.getString("kind"),rs.getString("currency"),
                rs.getBigDecimal("original_amount"),rs.getBigDecimal("known_usd"),rs.getLong("missing_usd_count")),tripId,tripId,tripId);
    }
}
