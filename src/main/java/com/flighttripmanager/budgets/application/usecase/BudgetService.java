package com.flighttripmanager.budgets.application.usecase;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.flighttripmanager.budgets.application.contract.*;
import com.flighttripmanager.budgets.application.port.in.BudgetManagement;
import com.flighttripmanager.budgets.application.port.out.*;
import com.flighttripmanager.budgets.application.mapper.BudgetMapper;
import com.flighttripmanager.budgets.domain.model.*;
import static com.flighttripmanager.budgets.application.contract.BudgetException.Reason.*;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class BudgetService implements BudgetManagement {
    private final BudgetTrips trips;private final BudgetStore store;private final BudgetMapper mapper;private final Clock clock;
    public BudgetService(BudgetTrips trips,BudgetStore store,BudgetMapper mapper,Clock clock){this.trips=trips;this.store=store;this.mapper=mapper;this.clock=clock;}
    @Override public BudgetView get(UUID tripId){
        var context=trips.inspect(tripId,false);var rows=store.aggregate(tripId);var limits=store.categories(tripId);var legLimits=store.legLimits(tripId);
        var categories=Arrays.stream(TransportCategory.values()).map(c->new BudgetView.CategoryView(c.name(),
            mapper.balance(BudgetBalance.calculate(limits.get(c),rows.stream().filter(r->r.category()==c).toList())))).toList();
        BigDecimal categorySum=limits.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal legSum=legLimits.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        var total=BudgetBalance.calculate(context.totalBudgetUsd(),rows);
        List<String> warnings=new ArrayList<>();
        warnings.add("EXPLICIT_TRANSPORT_EXPENSES_ONLY");
        if(total.forecast().missingUsdCount()>0)warnings.add("FORECAST_USD_INCOMPLETE");
        if(total.actual().missingUsdCount()>0)warnings.add("ACTUAL_USD_INCOMPLETE");
        if(total.remainingUsd()!=null&&total.remainingUsd().signum()<0)warnings.add("OVER_BUDGET");
        if(context.totalBudgetUsd()!=null){
            if(categorySum.compareTo(context.totalBudgetUsd())>0)warnings.add("CATEGORY_LIMITS_EXCEED_TOTAL");
            if(legSum.compareTo(context.totalBudgetUsd())>0)warnings.add("LEG_LIMITS_EXCEED_TOTAL");
        }
        return new BudgetView(tripId,context.version(),mapper.balance(total),categories,
            mapper.balance(BudgetBalance.calculate(null,rows.stream().filter(r->r.legId()==null).toList())),
            categorySum,legSum,"MANUAL_LEDGER_ONLY",List.copyOf(warnings));
    }
    @Override @Transactional public BudgetView put(UUID tripId,PutBudget command){
        return valid(()->{
            var c=edit(tripId,command.version());BigDecimal total=BudgetMoney.limit(command.totalBudgetUsd());
            if(command.categories()==null||command.categories().size()>5)throw error(INVALID_REQUEST);
            Map<TransportCategory,BigDecimal> limits=new EnumMap<>(TransportCategory.class);
            command.categories().forEach((key,value)->{
                var category=TransportCategory.valueOf(key);var limit=BudgetMoney.limit(value);
                if(limit==null)throw error(INVALID_REQUEST);limits.put(category,limit);
            });
            store.replaceCategories(tripId,limits);trips.update(tripId,c.version(),total);
            return get(tripId);
        });
    }
    @Override public LegBudgetView leg(UUID tripId,UUID legId){
        var context=trips.inspect(tripId,false);var leg=requireLeg(context,legId);
        return legView(context,leg,store.legLimits(tripId),store.aggregate(tripId));
    }
    @Override @Transactional public LegBudgetView putLeg(UUID tripId,UUID legId,PutLegBudget command){
        return valid(()->{
            var c=edit(tripId,command.version());requireLeg(c,legId);
            store.setLegLimit(tripId,legId,BudgetMoney.limit(command.amountUsd()));
            trips.update(tripId,c.version(),c.totalBudgetUsd());return leg(tripId,legId);
        });
    }
    @Override public BudgetPage<LegBudgetView> legs(UUID tripId,BudgetQuery query){
        var c=trips.inspect(tripId,false);var limits=store.legLimits(tripId);var rows=store.aggregate(tripId);
        var values=c.legs().stream().sorted(Comparator.comparing(BudgetTrips.Leg::id))
            .skip((long)query.page()*query.size()).limit(query.size()).map(l->legView(c,l,limits,rows)).toList();
        return new BudgetPage<>(values,query.page(),query.size(),c.legs().size(),(c.legs().size()+query.size()-1L)/query.size());
    }
    @Override @Transactional public ExpenseView createExpense(UUID tripId,ExpenseCommand command){
        return valid(()->{
            var c=edit(tripId,command.version());var expense=build(tripId,UUID.randomUUID(),command,c,null);
            store.save(expense,true);long version=trips.update(tripId,c.version(),c.totalBudgetUsd());
            return mapper.view(expense,version);
        });
    }
    @Override @Transactional public ExpenseView replaceExpense(UUID tripId,UUID id,ExpenseCommand command){
        return valid(()->{
            var c=edit(tripId,command.version());var old=require(tripId,id);var expense=build(tripId,id,command,c,old);
            store.save(expense,false);long version=trips.update(tripId,c.version(),c.totalBudgetUsd());
            return mapper.view(expense,version);
        });
    }
    @Override public ExpenseView expense(UUID tripId,UUID id){
        var c=trips.inspect(tripId,false);return mapper.view(require(tripId,id),c.version());
    }
    @Override public BudgetPage<ExpenseView> expenses(UUID tripId,BudgetQuery query){
        var c=trips.inspect(tripId,false);var page=store.expenses(tripId,query);
        return new BudgetPage<>(page.items().stream().map(e->mapper.view(e,c.version())).toList(),page.page(),page.size(),page.totalElements(),page.totalPages());
    }
    @Override @Transactional public void deleteExpense(UUID tripId,UUID id,long version){
        var c=edit(tripId,version);require(tripId,id);store.delete(tripId,id);trips.update(tripId,c.version(),c.totalBudgetUsd());
    }
    private TransportExpense build(UUID tripId,UUID id,ExpenseCommand command,BudgetTrips.Context context,TransportExpense old){
        if(command.category()==null)throw error(INVALID_REQUEST);
        var category=TransportCategory.valueOf(command.category());
        if(command.legId()!=null&&!requireLeg(context,command.legId()).transportType().equals(category.transport()))throw error(INVALID_LEG);
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);if(now.isBefore(context.updatedAt()))now=context.updatedAt();
        return new TransportExpense(id,tripId,command.legId(),category,command.description(),command.referenceKey(),
            mapper.money(command.estimated()),mapper.money(command.actual()),old==null?now:old.createdAt(),now);
    }
    private LegBudgetView legView(BudgetTrips.Context c,BudgetTrips.Leg leg,Map<UUID,BigDecimal> limits,List<SpendingAggregate> rows){
        return new LegBudgetView(leg.id(),leg.transportType(),c.version(),
            mapper.balance(BudgetBalance.calculate(limits.get(leg.id()),rows.stream().filter(r->leg.id().equals(r.legId())).toList())));
    }
    private BudgetTrips.Context edit(UUID tripId,Long version){
        if(version==null||version<0)throw error(INVALID_REQUEST);
        var c=trips.inspect(tripId,true);if(c.version()!=version)throw error(VERSION_CONFLICT);return c;
    }
    private BudgetTrips.Leg requireLeg(BudgetTrips.Context context,UUID legId){
        return context.legs().stream().filter(l->l.id().equals(legId)).findFirst().orElseThrow(()->error(INVALID_LEG));
    }
    private TransportExpense require(UUID tripId,UUID id){return store.expense(tripId,id).orElseThrow(()->error(NOT_FOUND));}
    private static BudgetException error(BudgetException.Reason reason){return new BudgetException(reason);}
    private static <T>T valid(Supplier<T> action){
        try{return action.get();}catch(BudgetRuleViolation|IllegalArgumentException e){throw error(INVALID_REQUEST);}
    }
}
