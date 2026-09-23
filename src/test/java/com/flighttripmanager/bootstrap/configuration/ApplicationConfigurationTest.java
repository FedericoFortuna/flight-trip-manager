package com.flighttripmanager.bootstrap.configuration;

import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

    private final ApplicationConfiguration configuration = new ApplicationConfiguration();

    @Test
    void clockUsesUtcRegardlessOfHostTimezone() {
        assertThat(configuration.clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void errorDocumentationIsAvailableBeforeFunctionalEndpointsExist() {
        var document = new io.swagger.v3.oas.models.OpenAPI();
        configuration.standardErrorDocumentation().customise(document);
        assertThat(document.getComponents().getSchemas()).containsKey("ApiError");
    }

    @Test
    void commonErrorsDoNotOverwriteEndpointSpecificDocumentation() {
        var customError = new io.swagger.v3.oas.models.responses.ApiResponse().description("Endpoint-specific validation");
        var operation = new io.swagger.v3.oas.models.Operation().responses(
                new io.swagger.v3.oas.models.responses.ApiResponses().addApiResponse("400", customError));
        var document = new io.swagger.v3.oas.models.OpenAPI()
                .components(new io.swagger.v3.oas.models.Components())
                .paths(new io.swagger.v3.oas.models.Paths().addPathItem("/api/v1/example",
                        new io.swagger.v3.oas.models.PathItem().get(operation)));
        configuration.standardErrorDocumentation().customise(document);
        assertThat(operation.getResponses().get("400")).isSameAs(customError);
        assertThat(operation.getResponses().get("500").getContent().get("application/json")
                .getSchema().get$ref()).isEqualTo("#/components/schemas/ApiError");
    }
}
