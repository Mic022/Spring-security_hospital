package com.roles.usermanagement.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

/**
 * Configuración para permitir solicitudes CORS (Cross-Origin Resource Sharing).
 */
@Configuration
public class CorsConfig {

    /**
     * Define la configuración CORS permitida para la aplicación.
     *
     * @return Fuente de configuración CORS.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:*}") List<String> origins) {
        // Configuración de CORS
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        // Orígenes permitidos según app.cors.allowed-origins: "*" en desarrollo, el dominio del frontend en producción.
        corsConfiguration.setAllowedOrigins(origins);

        // Permitir métodos HTTP específicos (GET, POST, PUT, DELETE, etc.)
        corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));

        // Permitir todos los encabezados en las solicitudes
        corsConfiguration.setAllowedHeaders(Arrays.asList("*"));

        // Configuración de CORS basada en URL
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);

        return source;
    }
}
