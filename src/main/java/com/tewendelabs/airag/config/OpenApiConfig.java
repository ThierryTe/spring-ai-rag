package com.tewendelabs.airag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;


@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI enterpriseAiKnowledgeAssistantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Compliance Copilot API")
                        .description("API RAG d'assistance a la conformite : upload de documents, "
                                + "questions/reponses avec citations, garde-fous anti-hallucination.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}
