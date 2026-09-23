package com.flighttripmanager.bootstrap.configuration;

import java.time.Clock;

import com.flighttripmanager.shared.api.error.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Flight Trip Manager API")
                .version("v1")
                .description("Backend modular de gestión de viajes. Los endpoints funcionales se incorporan por cards."));
    }

    @Bean
    public OpenApiCustomizer standardErrorDocumentation() {
        return document -> {
            if (document.getComponents() == null) {
                document.setComponents(new Components());
            }
            ModelConverters.getInstance().read(ApiError.class).forEach(document.getComponents()::addSchemas);
            if (document.getPaths() != null) {
                document.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                    operation.getResponses().putIfAbsent("400", errorResponse("Invalid request"));
                    operation.getResponses().putIfAbsent("500", errorResponse("Unexpected server error"));
                }));
            }
        };
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }
}
