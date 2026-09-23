package com.flighttripmanager.architecture;

import java.nio.file.Path;
import java.util.stream.Stream;

import com.flighttripmanager.FlightTripManagerApplication;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {
    @TestFactory
    Stream<DynamicTest> productionArchitecture() throws Exception {
        Path production = Path.of(FlightTripManagerApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var classes = new ClassFileImporter().importPath(production);
        assertThat(classes.contain(FlightTripManagerApplication.class)).isTrue();
        assertThat(classes.stream()).allMatch(type -> type.getName().startsWith("com.flighttripmanager."));
        return new ArchitectureRules("com.flighttripmanager").all().stream()
                .map(rule -> DynamicTest.dynamicTest(rule.getDescription(), () -> rule.check(classes)));
    }
}
