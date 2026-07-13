package ru.orbitamarket.payments;

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
  OpenAPI paymentsOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("OrbitaMarket Payments API")
                .version("1.0.0")
                .description("Accounts, geocredit top-ups and balance operations."))
        .servers(List.of(new Server().url("/payments").description("API Gateway")))
        .security(
            List.of(
                new SecurityRequirement().addList("bearerAuth"),
                new SecurityRequirement().addList("X-User-Id")))
        .components(securityComponents());
  }

  private Components securityComponents() {
    return new Components()
        .addSecuritySchemes(
            "bearerAuth",
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT"))
        .addSecuritySchemes(
            "X-User-Id",
            new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-User-Id"));
  }
}
