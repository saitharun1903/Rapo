package com.rideflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    /** Packages holding the records that appear in request and response bodies. */
    private static final String[] BODY_PACKAGES = {"com.rideflow.dto", "com.rideflow.ai", "com.rideflow.geospatial"};

    @Bean
    OpenAPI rideFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideFlow API")
                        .version("v1")
                        .description("Real-time ride-hailing platform API. Conventions: docs/api.md"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                // Relative, so the same document is valid wherever the API is deployed (and stable in the
                // committed contract, docs/openapi.json).
                .servers(List.of(new Server().url("/")));
    }

    @Bean
    OpenApiCustomizer recordSchemaNullability() {
        return new RecordSchemaNullability(BODY_PACKAGES);
    }
}
