package com.flighttripmanager.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import testsupport.ErrorProbeConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Import(ErrorProbeConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class BootstrapIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private Environment environment;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void migrationsCreateAllModuleSchemasAndCanRunAgain() {
        List<String> schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata", String.class);
        assertThat(schemas).contains("trips", "catalog", "flights", "flightsearch",
                "tracking", "connections", "budgets");
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from public.flyway_schema_history where success", Long.class)).isEqualTo(3L);
    }

    @Test
    void hibernateOnlyValidatesAndOpenSessionInViewIsDisabled() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
    }

    @Test
    void healthIncludesDatabaseReadinessWithoutExposingDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components", "password");
    }

    @Test
    void swaggerAndOpenApiAreAvailableForDevelopment() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Flight Trip Manager API", "\"version\":\"v1\"");
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/configprops").statusCode()).isEqualTo(404);
    }

    @ParameterizedTest
    @CsvSource({"/api/v1/does-not-exist,404,RESOURCE_NOT_FOUND", "/api/v1/probe/failure,500,INTERNAL_ERROR",
            "/api/v1/probe/dispatch,400,INVALID_REQUEST", "/api/v1/probe/count?count=0,400,INVALID_REQUEST",
            "/error,500,INTERNAL_ERROR"})
    void errorContractWorksThroughRealHttpAndServletDispatch(String path, int status, String code,
            CapturedOutput output) throws Exception {
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).isEqualTo(status);
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.has("message")).isTrue();
        assertThat(body.get("details").isObject()).isTrue();
        assertThat(java.time.Instant.parse(body.get("timestamp").asText())).isNotNull();
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(response.body()).doesNotContain("ABC123", "private-token", "stackTrace", "exception");
        assertThat(output.getOut()).doesNotContain("ABC123", "private-token")
                .contains("\"operation\"", "\"durationMs\"", "\"requestId\"");
    }

    @Test
    void openApiPublishesSharedErrorSchema() throws Exception {
        var document = new com.fasterxml.jackson.databind.ObjectMapper().readTree(get("/v3/api-docs").body());
        assertThat(document.at("/components/schemas/ApiError/properties/code").isMissingNode()).isFalse();
        assertThat(document.at("/paths/~1error").isMissingNode()).isTrue();
    }

    @Test
    void unsupportedResponseFormatKeepsErrorContract() throws Exception {
        String port = environment.getRequiredProperty("local.server.port");
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/probe/representation/json"))
                .header("Accept", "application/xml").timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(406);
        assertThat(response.body()).contains("NOT_ACCEPTABLE");
    }

    private HttpResponse<String> get(String path) throws Exception {
        String port = environment.getRequiredProperty("local.server.port");
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
