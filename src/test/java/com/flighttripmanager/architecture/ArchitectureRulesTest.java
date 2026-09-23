package com.flighttripmanager.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.tools.ToolProvider;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Compile intentionally valid/invalid bytecode outside target/classes. No production fixtures. */
class ArchitectureRulesTest {
    private static final String ROOT = "architecturefixtures";
    @TempDir static Path workspace;
    private static JavaClasses fixtures;

    @BeforeAll
    static void compileFixtures() throws Exception {
        Map<String, String> sources = Map.ofEntries(
                Map.entry("providerstub.RawOffer", "package providerstub; public class RawOffer {}"),
                source("shared.domain.Money", "public record Money(java.math.BigDecimal amount) {}"),
                source("shared.api.error.ApiError", "public record ApiError(String code) {}"),
                source("trips.api.dto.ErrorReference", "public class ErrorReference { architecturefixtures.shared.api.error.ApiError error; }"),
                source("shared.infrastructure.InternalHelper", "public class InternalHelper {}"),
                source("flights.infrastructure.adapter.SharedHelperBackdoor", "public class SharedHelperBackdoor { architecturefixtures.shared.infrastructure.InternalHelper helper; }"),
                source("catalog.application.contract.CatalogView", "public record CatalogView(String name) {}"),
                source("trips.domain.model.Trip", "public class Trip { architecturefixtures.shared.domain.Money budget; java.time.LocalDate date; }"),
                source("trips.application.contract.TripView", "public record TripView(java.util.UUID id, String[] notes) {}"),
                source("trips.application.port.in.ReadTrip", "public interface ReadTrip { architecturefixtures.trips.application.contract.TripView read(); }"),
                source("trips.application.port.out.TripStore", "public interface TripStore {}"),
                source("trips.application.usecase.ReadTripService", "public class ReadTripService implements architecturefixtures.trips.application.port.in.ReadTrip { architecturefixtures.trips.application.port.out.TripStore store; public architecturefixtures.trips.application.contract.TripView read() { return null; } }"),
                source("trips.infrastructure.persistence.entity.TripJpaEntity", "@jakarta.persistence.Entity public class TripJpaEntity { @jakarta.persistence.Id java.util.UUID id; }"),
                source("trips.infrastructure.persistence.repository.TripJpaRepository", "public interface TripJpaRepository extends org.springframework.data.repository.Repository<architecturefixtures.trips.infrastructure.persistence.entity.TripJpaEntity, java.util.UUID> {}"),
                source("trips.infrastructure.provider.ExternalAdapter", "public class ExternalAdapter {}"),
                source("trips.domain.BadSpring", "@org.springframework.stereotype.Component public class BadSpring {}"),
                source("trips.domain.BadJpa", "@jakarta.persistence.Entity public class BadJpa {}"),
                source("trips.domain.BadHttp", "public class BadHttp { java.net.http.HttpClient client; }"),
                source("trips.domain.BadJson", "public class BadJson { com.fasterxml.jackson.databind.JsonNode payload; }"),
                source("trips.domain.BadProvider", "public class BadProvider { architecturefixtures.trips.infrastructure.provider.ExternalAdapter adapter; }"),
                source("trips.domain.BadExternal", "public class BadExternal { providerstub.RawOffer raw; }"),
                source("trips.application.usecase.BadExternal", "public class BadExternal { providerstub.RawOffer raw; }"),
                source("trips.application.contract.BadExternal", "public record BadExternal(providerstub.RawOffer raw) {}"),
                source("trips.domain.BadPersistence", "public class BadPersistence { architecturefixtures.trips.infrastructure.persistence.entity.TripJpaEntity entity; }"),
                source("trips.application.usecase.BadPersistence", "public class BadPersistence { architecturefixtures.trips.infrastructure.persistence.repository.TripJpaRepository repository; }"),
                source("trips.application.usecase.BadWeb", "public class BadWeb { org.springframework.web.client.RestTemplate client; }"),
                source("trips.application.contract.LeakedDomain", "public record LeakedDomain(architecturefixtures.trips.domain.model.Trip trip) {}"),
                source("trips.application.contract.LeakedJpa", "public record LeakedJpa(architecturefixtures.trips.infrastructure.persistence.entity.TripJpaEntity entity) {}"),
                source("trips.application.contract.LeakedJson", "public record LeakedJson(com.fasterxml.jackson.databind.JsonNode payload) {}"),
                source("trips.api.controller.GoodController", "@org.springframework.web.bind.annotation.RestController public class GoodController { architecturefixtures.trips.application.port.in.ReadTrip port; }"),
                source("trips.api.controller.RepositoryController", "public class RepositoryController { architecturefixtures.trips.infrastructure.persistence.repository.TripJpaRepository repository; }"),
                source("trips.api.controller.ServiceController", "public class ServiceController { architecturefixtures.trips.application.usecase.ReadTripService service; }"),
                source("trips.api.controller.DomainController", "public class DomainController { architecturefixtures.trips.domain.model.Trip trip; }"),
                source("trips.api.dto.MisplacedEntity", "@jakarta.persistence.Entity public class MisplacedEntity {}"),
                source("trips.infrastructure.MisplacedController", "@org.springframework.web.bind.annotation.RestController public class MisplacedController { architecturefixtures.trips.infrastructure.persistence.repository.TripJpaRepository repository; }"),
                source("flights.infrastructure.adapter.GoodTripAdapter", "public class GoodTripAdapter { architecturefixtures.trips.application.port.in.ReadTrip port; }"),
                source("flights.infrastructure.adapter.BadTripRepository", "public class BadTripRepository { architecturefixtures.trips.infrastructure.persistence.repository.TripJpaRepository repository; }"),
                source("flights.infrastructure.adapter.BadTripRepositoryArray", "public class BadTripRepositoryArray { architecturefixtures.trips.infrastructure.persistence.repository.TripJpaRepository[] repositories; }"),
                source("flights.infrastructure.adapter.BadTripDomain", "public class BadTripDomain { architecturefixtures.trips.domain.model.Trip trip; }"),
                source("flights.infrastructure.adapter.BadTripOutbound", "public class BadTripOutbound { architecturefixtures.trips.application.port.out.TripStore store; }"),
                source("flights.application.contract.FlightView", "public record FlightView(String number) {}"),
                source("trips.infrastructure.adapter.ReverseDependency", "public class ReverseDependency { architecturefixtures.flights.application.contract.FlightView view; }"),
                source("shared.infrastructure.BadBusinessDependency", "public class BadBusinessDependency { architecturefixtures.trips.application.contract.TripView view; }"),
                source("shared.infrastructure.persistence.SharedRepository", "public class SharedRepository {}"),
                source("flights.infrastructure.adapter.SharedRepositoryBackdoor", "public class SharedRepositoryBackdoor { architecturefixtures.shared.infrastructure.persistence.SharedRepository repository; }"),
                source("bootstrap.workflows.GoodWorkflow", "public class GoodWorkflow { architecturefixtures.trips.application.port.in.ReadTrip port; }"),
                source("bootstrap.workflows.BadWorkflow", "public class BadWorkflow { architecturefixtures.trips.application.usecase.ReadTripService service; }"),
                source("trips.application.contract.CycleTrip", "public class CycleTrip { architecturefixtures.flights.application.contract.CycleFlight flight; }"),
                source("flights.application.contract.CycleFlight", "public class CycleFlight { architecturefixtures.trips.application.contract.CycleTrip trip; }"),
                source("unknown.domain.Surprise", "public class Surprise {}"),
                source("trips.service.WrongLayer", "public class WrongLayer {}")
        );
        Path output = Files.createDirectories(workspace.resolve("classes"));
        List<String> arguments = new ArrayList<>(List.of("--release", "17", "-proc:none", "-classpath",
                System.getProperty("java.class.path"), "-d", output.toString()));
        for (var source : sources.entrySet()) {
            Path file = workspace.resolve("sources").resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Architecture fixtures require a JDK, not a JRE").isNotNull();
        assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new))).isZero();
        fixtures = new ClassFileImporter().importPath(output);
        assertThat(fixtures.size()).isEqualTo(sources.size());
    }

    @TestFactory
    Stream<DynamicTest> rejectIllegalDependencies() {
        return Stream.of(
                bad("domain Spring annotation", ArchitectureRules::domain, "trips.domain.BadSpring"),
                bad("domain JPA annotation", ArchitectureRules::domain, "trips.domain.BadJpa"),
                bad("domain HTTP", ArchitectureRules::domain, "trips.domain.BadHttp"),
                bad("domain Jackson", ArchitectureRules::domain, "trips.domain.BadJson"),
                bad("domain provider adapter", ArchitectureRules::domain, "trips.domain.BadProvider"),
                bad("domain external SDK", ArchitectureRules::domain, "trips.domain.BadExternal"),
                bad("application external SDK", ArchitectureRules::application, "trips.application.usecase.BadExternal"),
                bad("contract external SDK", ArchitectureRules::contracts, "trips.application.contract.BadExternal"),
                bad("domain JPA model", ArchitectureRules::domain, "trips.domain.BadPersistence"),
                bad("application persistence", ArchitectureRules::application, "trips.application.usecase.BadPersistence"),
                bad("application HTTP", ArchitectureRules::application, "trips.application.usecase.BadWeb"),
                bad("contract domain model", ArchitectureRules::contracts, "trips.application.contract.LeakedDomain"),
                bad("contract JPA model", ArchitectureRules::contracts, "trips.application.contract.LeakedJpa"),
                bad("contract JSON model", ArchitectureRules::contracts, "trips.application.contract.LeakedJson"),
                bad("controller repository", ArchitectureRules::api, "trips.api.controller.RepositoryController"),
                bad("controller service", ArchitectureRules::api, "trips.api.controller.ServiceController"),
                bad("controller domain", ArchitectureRules::controllers, "trips.api.controller.DomainController"),
                bad("misplaced entity", ArchitectureRules::entities, "trips.api.dto.MisplacedEntity"),
                bad("controller outside API", ArchitectureRules::layout, "trips.infrastructure.MisplacedController"),
                bad("cross-module repository", ArchitectureRules::modules, "flights.infrastructure.adapter.BadTripRepository"),
                bad("cross-module repository array", ArchitectureRules::modules, "flights.infrastructure.adapter.BadTripRepositoryArray"),
                bad("cross-module domain", ArchitectureRules::modules, "flights.infrastructure.adapter.BadTripDomain"),
                bad("cross-module outbound port", ArchitectureRules::modules, "flights.infrastructure.adapter.BadTripOutbound"),
                bad("reverse dependency", ArchitectureRules::modules, "trips.infrastructure.adapter.ReverseDependency"),
                bad("shared business dependency", ArchitectureRules::modules, "shared.infrastructure.BadBusinessDependency"),
                bad("shared repository backdoor", ArchitectureRules::modules, "flights.infrastructure.adapter.SharedRepositoryBackdoor"),
                bad("shared infrastructure backdoor", ArchitectureRules::modules, "flights.infrastructure.adapter.SharedHelperBackdoor"),
                bad("workflow service", ArchitectureRules::workflows, "bootstrap.workflows.BadWorkflow"),
                bad("module cycle", ArchitectureRules::cycles, "trips.application.contract.CycleTrip", "flights.application.contract.CycleFlight"),
                bad("undeclared module", ArchitectureRules::layout, "unknown.domain.Surprise"),
                bad("undeclared layer", ArchitectureRules::layout, "trips.service.WrongLayer")
        );
    }

    @TestFactory
    Stream<DynamicTest> acceptLegalHexagonalDependencies() {
        JavaClasses valid = select("shared.domain.Money", "shared.api.error.ApiError", "trips.api.dto.ErrorReference", "catalog.application.contract.CatalogView",
                "trips.domain.model.Trip", "trips.application.contract.TripView", "trips.application.port.in.ReadTrip",
                "trips.application.port.out.TripStore", "trips.application.usecase.ReadTripService",
                "trips.infrastructure.persistence.entity.TripJpaEntity", "trips.infrastructure.persistence.repository.TripJpaRepository",
                "trips.api.controller.GoodController", "flights.infrastructure.adapter.GoodTripAdapter", "bootstrap.workflows.GoodWorkflow");
        return new ArchitectureRules(ROOT).all().stream().map(rule ->
                DynamicTest.dynamicTest("accept: " + rule.getDescription(), () -> rule.check(valid)));
    }

    private DynamicTest bad(String description, Function<ArchitectureRules, ArchRule> rule, String... names) {
        return DynamicTest.dynamicTest("reject: " + description, () -> {
            var result = rule.apply(new ArchitectureRules(ROOT)).evaluate(select(names));
            assertThat(result.hasViolation()).as(description).isTrue();
            assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains(ROOT + "."));
        });
    }

    private static JavaClasses select(String... names) {
        List<String> qualified = Stream.of(names).map(name -> ROOT + "." + name).toList();
        JavaClasses selection = fixtures.that(new DescribedPredicate<>("selected fixture classes") {
            @Override public boolean test(JavaClass type) { return qualified.contains(type.getName()); }
        });
        assertThat(selection.size()).isEqualTo(names.length);
        return selection;
    }

    private static Map.Entry<String, String> source(String name, String declaration) {
        String qualified = ROOT + "." + name;
        String packageName = qualified.substring(0, qualified.lastIndexOf('.'));
        return Map.entry(qualified, "package " + packageName + ";\n" + declaration);
    }
}
