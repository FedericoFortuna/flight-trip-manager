package com.flighttripmanager.flightsearch.infrastructure.configuration;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter @Validated @ConfigurationProperties("flight-search")
public class SearchProperties {
    public enum Mode { DISABLED, MOCK, DUFFEL }
    @NotNull private Mode mode=Mode.DISABLED;
    @Min(1) @Max(300) private int resultTtlSeconds=180;
    @Min(1) @Max(128) private int cacheEntries=32;
    @Min(1) @Max(8) private int concurrentSearches=2;
    @Min(1) @Max(120) private int deadlineSeconds=45;
    @Min(1) @Max(100) private int maxOffersPerVariant=100;
    @Min(50) @Max(10000) private int connectTimeoutMs=1500;
    @Min(50) @Max(30000) private int requestTimeoutMs=8000;
    @Min(2000) @Max(60000) private int supplierTimeoutMs=5000;
    @Min(1) @Max(3) private int maxAttempts=2;
    @Min(1) @Max(1000) private int retryDelayMs=100;
    @Min(1) @Max(2000) private int maxRetryDelayMs=1000;
    @Min(1) @Max(1000) private int requestsPerWindow=30;
    @Min(1) @Max(3600) private int windowSeconds=60;
    @Min(1) @Max(20) private int circuitThreshold=3;
    @Min(1) @Max(300) private int circuitSeconds=30;
    @Min(1024) @Max(16777216) private int maxResponseBytes=4194304;
    @NotNull private String duffelToken="";
    @DecimalMin("0") @DecimalMax("1000") private BigDecimal priceWeight=new BigDecimal("40");
    @DecimalMin("0") @DecimalMax("1000") private BigDecimal durationWeight=new BigDecimal("25");
    @DecimalMin("0") @DecimalMax("1000") private BigDecimal stopsWeight=new BigDecimal("15");
    @DecimalMin("0") @DecimalMax("1000") private BigDecimal scheduleWeight=new BigDecimal("10");
    @DecimalMin("0") @DecimalMax("1000") private BigDecimal baggageWeight=new BigDecimal("10");
    @DecimalMin("0") @DecimalMax("1000") private BigDecimal missingCostsWeight=new BigDecimal("5");
    @Override public String toString(){return "SearchProperties[credentials redacted]";}
}
