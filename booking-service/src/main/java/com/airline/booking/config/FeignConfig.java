package com.airline.booking.config;

import com.airline.booking.client.FlightsErrorDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder flightsErrorDecoder(ObjectMapper objectMapper) {
        return new FlightsErrorDecoder(objectMapper);
    }
}
