package com.flighttripmanager.trips.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

public record Trip(UUID id, String name, LocalDate startDate, LocalDate endDate, BigDecimal totalBudgetUsd,
        TripStatus manualStatusOverride, long version, Instant createdAt, Instant updatedAt, List<TripLeg> legs) {
    public Trip {
        if (id == null || name == null || name.isBlank() || name.strip().length() > 200
                || startDate == null || endDate == null || createdAt == null || updatedAt == null || version < 0) {
            throw new TripRuleViolation("trip");
        }
        name = name.strip();
        if (startDate.getYear() < 1 || endDate.getYear() > 9999 || endDate.isBefore(startDate)) {
            throw new TripRuleViolation("endDate");
        }
        if (totalBudgetUsd != null) {
            try { totalBudgetUsd = totalBudgetUsd.setScale(2, RoundingMode.UNNECESSARY); }
            catch (ArithmeticException error) { throw new TripRuleViolation("totalBudgetUsd"); }
            if (totalBudgetUsd.signum() < 0 || totalBudgetUsd.precision() > 19) throw new TripRuleViolation("totalBudgetUsd");
        }
        legs = List.copyOf(legs);
        if (legs.size() > 500) throw new TripRuleViolation("legs");
        Set<UUID> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (TripLeg leg : legs) {
            if (!leg.tripId().equals(id) || !ids.add(leg.id())) throw new TripRuleViolation("legs");
            if (leg.departureDate() != null && (leg.departureDate().isBefore(startDate) || leg.departureDate().isAfter(endDate))) {
                throw new TripRuleViolation("departureDate");
            }
            if (leg.arrivalDate() != null && leg.arrivalDate().isAfter(endDate)) throw new TripRuleViolation("arrivalDate");
            if (leg.manualOrder() != null && (!orders.add(leg.manualOrder()) || leg.manualOrder() >= legs.size())) {
                throw new TripRuleViolation("manualOrder");
            }
        }
        if (!orders.isEmpty() && orders.size() != legs.size()) throw new TripRuleViolation("manualOrder");
    }
    public List<TripLeg> orderedLegs() {
        // Known hours precede unknown hours on a date; unknown hours use creation time, never an invented midnight.
        boolean manual = !legs.isEmpty() && legs.get(0).manualOrder() != null;
        Comparator<TripLeg> fallback = Comparator.comparing(TripLeg::createdAt).thenComparing(TripLeg::id);
        if (manual) return legs.stream().sorted(Comparator.comparing(TripLeg::manualOrder)).toList();
        Comparator<TripLeg> automatic = Comparator.comparing(TripLeg::departureDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TripLeg::departureDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(fallback);
        return legs.stream().sorted(automatic).toList();
    }
    public TripStatus derivedStatus(Instant now) {
        if (legs.isEmpty()) return TripStatus.PLANNING;
        List<LegStatus> active = legs.stream().map(leg -> leg.status(now)).filter(status -> status != LegStatus.CANCELLED).toList();
        if (active.isEmpty()) return TripStatus.CANCELLED;
        if (active.stream().allMatch(status -> status == LegStatus.COMPLETED)) return TripStatus.COMPLETED;
        if (active.contains(LegStatus.IN_PROGRESS)) return TripStatus.IN_PROGRESS;
        long covered = active.stream().filter(status -> status == LegStatus.BOOKED
                || status == LegStatus.UPCOMING || status == LegStatus.COMPLETED).count();
        if (covered == active.size()) {
            LocalDate today = now.atOffset(ZoneOffset.UTC).toLocalDate();
            if (startDate.isAfter(today)) return TripStatus.UPCOMING;
            return TripStatus.IN_PROGRESS;
        }
        return covered > 0 ? TripStatus.PARTIALLY_BOOKED : TripStatus.PLANNING;
    }
    public TripStatus status(Instant now) {
        return manualStatusOverride == null ? derivedStatus(now) : manualStatusOverride;
    }
    public Trip changed(String name, LocalDate start, LocalDate end, BigDecimal budget, TripStatus override,
            List<TripLeg> legs, Instant now) {
        return new Trip(id, name, start, end, budget, override, Math.addExact(version, 1), createdAt, now, legs);
    }
    public Trip withLegs(List<TripLeg> legs, Instant now) {
        return changed(name, startDate, endDate, totalBudgetUsd, manualStatusOverride, legs, now);
    }
}
