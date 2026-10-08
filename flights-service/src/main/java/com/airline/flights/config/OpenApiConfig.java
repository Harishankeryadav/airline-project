package com.airline.flights.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flightsServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Flights Service API")
                .version("v1")
                .description("Cities, airports, airplanes, flights, flight search and seat inventory."));
    }
}
