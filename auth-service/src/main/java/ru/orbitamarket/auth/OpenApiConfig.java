package ru.orbitamarket.auth;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {
  @Bean
  OpenAPI authOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("OrbitaMarket Auth API")
                .version("1.0.0")
                .description("Registration, login and user profile management."))
        .servers(List.of(new Server().url("/auth").description("API Gateway")))
        .security(List.of(new SecurityRequirement().addList("bearerAuth")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
