package com.agendabarber.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("default")
public class OpenApiConfig {

    @Bean
    OpenAPI agendaBarberOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Agenda Barber API")
                .description("API for the Agenda Barber Android application. No database secrets are exposed.")
                .version("0.0.1"));
    }
}
