package com.flighttripmanager.flights.domain.model;
import java.math.*;
import java.util.Currency;
public record FlightMoney(BigDecimal amount, String currency, BigDecimal amountUsd) {
    public FlightMoney {
        amount = amount(amount);
        currency = FlightValues.code(currency, 3, "currency");
        try { Currency.getInstance(currency == null ? "" : currency); }
        catch (IllegalArgumentException error) { throw new FlightRuleViolation("currency"); }
        if (amountUsd != null) amountUsd = amount(amountUsd);
        if ("USD".equals(currency)) {
            if (amountUsd != null && amountUsd.compareTo(amount) != 0) throw new FlightRuleViolation("amountUsd");
            amountUsd = amount;
        }
    }
    private static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new FlightRuleViolation("amount");
        try {
            BigDecimal exact = value.setScale(2, RoundingMode.UNNECESSARY);
            if (exact.precision() > 19) throw new FlightRuleViolation("amount");
            return exact;
        } catch (ArithmeticException error) { throw new FlightRuleViolation("amount"); }
    }
}
