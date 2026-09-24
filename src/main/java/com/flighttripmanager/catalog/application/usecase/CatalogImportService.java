package com.flighttripmanager.catalog.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.flighttripmanager.catalog.application.contract.*;
import com.flighttripmanager.catalog.application.mapper.CatalogImportDomainMapper;
import com.flighttripmanager.catalog.application.port.in.CatalogImport;
import com.flighttripmanager.catalog.application.port.out.*;
import com.flighttripmanager.catalog.domain.model.*;
import static com.flighttripmanager.catalog.application.contract.CatalogImportException.Reason.*;

@Service
public class CatalogImportService implements CatalogImport {
    private static final UUID VALIDATION_ID = new UUID(0, 0);
    private final CatalogImportStore store;
    private final CatalogImportDomainMapper mapper;
    private final Clock clock;

    public CatalogImportService(CatalogImportStore store, CatalogImportDomainMapper mapper, Clock clock) {
        this.store = store;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CatalogImportResult importBatch(CatalogImportBatch batch) {
        Instant syncedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        validate(batch, syncedAt);
        String source = batch.source().strip().toLowerCase(Locale.ROOT);
        store.acquireImportLock();
        List<ImportItemResult> items = new ArrayList<>();
        for (AirportImport input : list(batch.airports())) {
            String externalId = input.externalId().strip();
            var previous = store.airport(source, externalId);
            UUID id = previous.map(entry -> entry.value().id()).orElseGet(UUID::randomUUID);
            Airport candidate = mapper.airport(input, id, previous.map(entry -> entry.value().lastSyncedAt()).orElse(syncedAt));
            ImportStatus status = status(previous.map(StoredCatalogEntry::observedAt).orElse(null),
                    batch.observedAt(), previous.map(entry -> entry.value().equals(candidate)).orElse(false));
            if (status != ImportStatus.UNCHANGED) {
                store.save(mapper.airport(input, id, syncedAt), metadata(batch, source, externalId, syncedAt));
            }
            items.add(new ImportItemResult(CatalogKind.AIRPORT, externalId, id, status));
        }
        for (AirlineImport input : list(batch.airlines())) {
            String externalId = input.externalId().strip();
            var previous = store.airline(source, externalId);
            UUID id = previous.map(entry -> entry.value().id()).orElseGet(UUID::randomUUID);
            Airline candidate = mapper.airline(input, id, previous.map(entry -> entry.value().lastSyncedAt()).orElse(syncedAt));
            ImportStatus status = status(previous.map(StoredCatalogEntry::observedAt).orElse(null),
                    batch.observedAt(), previous.map(entry -> entry.value().equals(candidate)).orElse(false));
            if (status != ImportStatus.UNCHANGED) {
                store.save(mapper.airline(input, id, syncedAt), metadata(batch, source, externalId, syncedAt));
            }
            items.add(new ImportItemResult(CatalogKind.AIRLINE, externalId, id, status));
        }
        for (LocationImport input : list(batch.locations())) {
            String externalId = input.externalId().strip();
            var previous = store.location(source, externalId);
            UUID id = previous.map(entry -> entry.value().id()).orElseGet(UUID::randomUUID);
            Location candidate = mapper.location(input, id);
            ImportStatus status = status(previous.map(StoredCatalogEntry::observedAt).orElse(null),
                    batch.observedAt(), previous.map(entry -> entry.value().equals(candidate)).orElse(false));
            if (status != ImportStatus.UNCHANGED) store.save(candidate, metadata(batch, source, externalId, syncedAt));
            items.add(new ImportItemResult(CatalogKind.LOCATION, externalId, id, status));
        }
        return CatalogImportResult.from(items);
    }

    private static ImportStatus status(Instant previous, Instant incoming, boolean sameValues) {
        if (previous == null) return ImportStatus.CREATED;
        if (incoming.isBefore(previous)) throw new CatalogImportException(STALE_DATA, "observedAt");
        if (incoming.equals(previous)) {
            if (!sameValues) throw new CatalogImportException(VERSION_CONFLICT, "observedAt");
            return ImportStatus.UNCHANGED;
        }
        return ImportStatus.UPDATED;
    }

    private static ImportMetadata metadata(CatalogImportBatch batch, String source, String externalId, Instant syncedAt) {
        return new ImportMetadata(source, externalId, batch.observedAt(), syncedAt);
    }

    private void validate(CatalogImportBatch batch, Instant now) {
        if (batch == null) throw new CatalogImportException(INVALID_BATCH, "request");
        if (batch.source() == null || !batch.source().strip().matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) {
            throw new CatalogImportException(INVALID_BATCH, "source");
        }
        // PostgreSQL persists microseconds; reject finer versions rather than silently rounding them.
        if (batch.observedAt() == null || batch.observedAt().isAfter(now)
                || batch.observedAt().getNano() % 1000 != 0
                || batch.observedAt().isBefore(Instant.EPOCH)) {
            throw new CatalogImportException(INVALID_BATCH, "observedAt");
        }
        long count = (long) list(batch.airports()).size() + list(batch.airlines()).size() + list(batch.locations()).size();
        if (count < 1 || count > 500) throw new CatalogImportException(INVALID_BATCH, "items");
        validateItems(list(batch.airports()), "airports", AirportImport::externalId, input -> {
            Objects.requireNonNull(input.active());
            return mapper.airport(input, VALIDATION_ID, now);
        });
        validateItems(list(batch.airlines()), "airlines", AirlineImport::externalId, input -> {
            Objects.requireNonNull(input.active());
            return mapper.airline(input, VALIDATION_ID, now);
        });
        validateItems(list(batch.locations()), "locations", LocationImport::externalId,
                input -> mapper.location(input, VALIDATION_ID));
    }

    private static <T> void validateItems(List<T> items, String field, Function<T, String> externalId, Function<T, ?> validate) {
        Set<String> identities = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            try {
                T item = Objects.requireNonNull(items.get(i));
                String id = Objects.requireNonNull(externalId.apply(item)).strip();
                if (id.isEmpty() || id.length() > 100 || id.codePoints().anyMatch(Character::isISOControl)
                        || !identities.add(id)) throw new IllegalArgumentException();
                validate.apply(item);
            } catch (IllegalArgumentException | NullPointerException | java.time.DateTimeException | ArithmeticException exception) {
                throw new CatalogImportException(INVALID_BATCH, field + "[" + i + "]");
            }
        }
    }

    private static <T> List<T> list(List<T> items) { return items == null ? List.of() : items; }
}
