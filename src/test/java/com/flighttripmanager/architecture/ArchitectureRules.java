package com.flighttripmanager.architecture;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Executable dependency policy; only production bytecode is supplied by ArchitectureTest. */
final class ArchitectureRules {
    private static final Map<String, Set<String>> DEPENDENCIES = Map.of(
            "catalog", Set.of("shared"), "trips", Set.of("catalog", "shared"),
            "flights", Set.of("trips", "catalog", "shared"),
            "flightsearch", Set.of("trips", "catalog", "shared"),
            "budgets", Set.of("trips", "shared"),
            "tracking", Set.of("flights", "catalog", "shared"),
            "connections", Set.of("trips", "flights", "catalog", "shared"), "shared", Set.of());
    private final String root;

    ArchitectureRules(String root) {
        this.root = root;
    }

    List<ArchRule> all() {
        return List.of(layout(), domain(), application(), contracts(), api(), controllers(), modules(), workflows(), entities(), cycles());
    }

    ArchRule layout() {
        return classes().should(new ArchCondition<>("belong to a declared module and layer") {
            @Override public void check(JavaClass type, ConditionEvents events) {
                String path = path(type);
                boolean legal = type.getName().equals(root + ".FlightTripManagerApplication")
                        || type.getSimpleName().equals("package-info")
                        || path.startsWith("bootstrap.configuration.") || path.startsWith("bootstrap.workflows.")
                        || (DEPENDENCIES.containsKey(module(type))
                            && Set.of("domain", "application", "infrastructure", "api").contains(layer(type)));
                if (isController(type) && !layer(type).equals("api")) {
                    legal = false;
                }
                if (!legal) {
                    events.add(SimpleConditionEvent.violated(type, "Undeclared module/layer: " + type.getName()));
                }
            }
        });
    }

    ArchRule domain() {
        return dependencies("keep domain pure and confined to its own domain or shared domain", dependency -> {
            JavaClass from = dependency.getOriginClass();
            JavaClass to = target(dependency);
            if (!layer(from).equals("domain")) return false;
            return !(jdk(to) || (internal(to) && layer(to).equals("domain")
                    && (module(from).equals(module(to)) || module(to).equals("shared"))));
        });
    }

    ArchRule application() {
        return dependencies("keep application independent of API, persistence and providers", dependency -> {
            if (!layer(dependency.getOriginClass()).equals("application")) return false;
            JavaClass to = target(dependency);
            return !(jdk(to) || (internal(to) && Set.of("domain", "application").contains(layer(to)))
                    || to.getName().startsWith("org.springframework.stereotype.")
                    || to.getName().startsWith("org.springframework.transaction.annotation.")
                    || to.getName().startsWith("org.slf4j."));
        });
    }

    ArchRule contracts() {
        return dependencies("expose only pure application contracts and inbound ports", dependency -> {
            JavaClass from = dependency.getOriginClass();
            JavaClass to = target(dependency);
            if (!publicContract(from)) return false;
            return !(jdk(to) || publicContract(to)
                    || (internal(to) && path(to).startsWith("shared.domain.")));
        });
    }

    ArchRule api() {
        return dependencies("keep API away from persistence and application implementations", dependency -> {
            if (!layer(dependency.getOriginClass()).equals("api")) return false;
            JavaClass to = target(dependency);
            return layer(to).equals("infrastructure")
                    || (layer(to).equals("application") && !publicContract(to))
                    || path(to).contains(".domain.repository.")
                    || to.getName().startsWith("org.springframework.data.")
                    || to.getName().startsWith("jakarta.persistence.");
        });
    }

    ArchRule modules() {
        return dependencies("follow the module dependency matrix through public contracts", dependency -> {
            JavaClass from = dependency.getOriginClass();
            JavaClass to = target(dependency);
            if (!internal(to) || module(from).equals(module(to)) || module(from).equals("bootstrap")) return false;
            if (!DEPENDENCIES.getOrDefault(module(from), Set.of()).contains(module(to))) return true;
            if (module(to).equals("shared")) {
                if (path(to).contains(".domain.repository.") || path(to).contains(".infrastructure.persistence.")) return true;
                return switch (layer(to)) {
                    case "domain" -> false;
                    case "application" -> !publicContract(to);
                    // ApiError is the explicitly shared HTTP response contract, not an implementation.
                    case "api" -> !(layer(from).equals("api") && to.getName().equals(root + ".shared.api.error.ApiError"));
                    default -> true;
                };
            }
            return !publicContract(to);
        });
    }

    ArchRule controllers() {
        return dependencies("keep controllers on inbound ports and API DTOs, not domain models", dependency -> {
            JavaClass from = dependency.getOriginClass();
            boolean controller = isController(from)
                    || (layer(from).equals("api") && from.getSimpleName().endsWith("Controller"));
            return controller && layer(target(dependency)).equals("domain");
        });
    }

    ArchRule workflows() {
        return dependencies("coordinate workflows using inbound ports and contracts only", dependency ->
                path(dependency.getOriginClass()).startsWith("bootstrap.workflows.")
                        && internal(target(dependency))
                        && !module(target(dependency)).equals("bootstrap")
                        && !publicContract(target(dependency)));
    }

    ArchRule entities() {
        return classes().should(new ArchCondition<>("keep JPA models inside infrastructure.persistence.entity") {
            @Override public void check(JavaClass type, ConditionEvents events) {
                boolean jpa = type.isAnnotatedWith("jakarta.persistence.Entity")
                        || type.isAnnotatedWith("jakarta.persistence.Embeddable")
                        || type.isAnnotatedWith("jakarta.persistence.MappedSuperclass");
                if (jpa && !path(type).startsWith(module(type) + ".infrastructure.persistence.entity.")) {
                    events.add(SimpleConditionEvent.violated(type, "Misplaced JPA model: " + type.getName()));
                }
            }
        });
    }

    ArchRule cycles() {
        return slices().matching(root + ".(*)..").should().beFreeOfCycles();
    }

    private ArchRule dependencies(String description, Predicate<Dependency> forbidden) {
        return classes().should(new ArchCondition<>(description) {
            @Override public void check(JavaClass type, ConditionEvents events) {
                type.getDirectDependenciesFromSelf().stream().filter(forbidden).forEach(dependency ->
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        });
    }

    private boolean publicContract(JavaClass type) {
        return internal(type) && (path(type).startsWith(module(type) + ".application.contract.")
                || path(type).startsWith(module(type) + ".application.port.in."));
    }

    private JavaClass target(Dependency dependency) {
        JavaClass type = dependency.getTargetClass();
        return type.isArray() ? type.getBaseComponentType() : type;
    }

    private boolean isController(JavaClass type) {
        return type.isAnnotatedWith("org.springframework.stereotype.Controller")
                || type.isMetaAnnotatedWith("org.springframework.stereotype.Controller");
    }

    private boolean jdk(JavaClass type) {
        return type.isPrimitive() || (type.getName().startsWith("java.")
                && !type.getName().startsWith("java.net.") && !type.getName().startsWith("java.sql."));
    }

    private boolean internal(JavaClass type) {
        return type.getName().startsWith(root + ".");
    }

    private String path(JavaClass type) {
        return internal(type) ? type.getName().substring(root.length() + 1) : "";
    }

    private String module(JavaClass type) {
        return path(type).split("\\.", 3)[0];
    }

    private String layer(JavaClass type) {
        String[] parts = path(type).split("\\.", 3);
        return parts.length > 1 ? parts[1] : "";
    }
}
