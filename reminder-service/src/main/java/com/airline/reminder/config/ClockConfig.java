package com.airline.reminder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected instead of calling Instant.now() so time-dependent logic can be tested. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
