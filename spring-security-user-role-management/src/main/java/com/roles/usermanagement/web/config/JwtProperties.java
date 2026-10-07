package com.roles.usermanagement.web.config;

import java.time.Duration;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix="security.jwt")
// El secreto HMAC256 debe tener al menos 32 caracteres para que no se pueda adivinar por fuerza bruta.
public record JwtProperties(@NotBlank @Size(min=32) String secret, @NotBlank String issuer, @NotNull Duration expiration) {
    @AssertTrue(message="security.jwt.expiration debe ser como mínimo un segundo")
    public boolean isExpirationValid() {
        return expiration != null && expiration.compareTo(Duration.ofSeconds(1)) >= 0;
    }
}
