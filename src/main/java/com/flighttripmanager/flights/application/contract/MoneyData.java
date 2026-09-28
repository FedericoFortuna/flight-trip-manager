package com.flighttripmanager.flights.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record MoneyData(BigDecimal amount, String currency, BigDecimal amountUsd) {}
