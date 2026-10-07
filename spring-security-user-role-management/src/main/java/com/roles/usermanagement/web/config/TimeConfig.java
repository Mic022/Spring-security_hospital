package com.roles.usermanagement.web.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación con la zona horaria del hospital.
 * Así los días transcurridos y restantes no dependen de la zona del servidor.
 */
@Configuration
public class TimeConfig {
    @Bean
    public Clock clock(@Value("${app.zona-horaria:America/Bogota}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
