package com.roles.usermanagement;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.roles.usermanagement.web.config.JwtProperties;
import com.roles.usermanagement.web.config.JwtUtil;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTests {
    private final JwtProperties settings=new JwtProperties("test-key-only-with-at-least-32-chars", "configured-issuer", Duration.ofMinutes(7));
    private final JwtUtil jwt=new JwtUtil(settings);

    @Test void usesConfiguredIssuerSecretAndExpiration() {
        String token=jwt.create("ana", "$2a$10$hash-actual");
        var decoded=JWT.require(Algorithm.HMAC256(settings.secret())).withIssuer(settings.issuer()).build().verify(token);
        assertThat(decoded.getExpiresAtAsInstant().getEpochSecond()-decoded.getIssuedAtAsInstant().getEpochSecond()).isEqualTo(420);
        assertThat(jwt.getUsername(token)).isEqualTo("ana");
        assertThat(jwt.isValid(token)).isTrue();
        assertThat(new JwtUtil(new JwtProperties("different-key-with-at-least-32-chars","configured-issuer",Duration.ofMinutes(7))).isValid(token)).isFalse();
        // La huella coincide solo con la contraseña con la que se emitió el token.
        assertThat(jwt.matchesPassword(token, "$2a$10$hash-actual")).isTrue();
        assertThat(jwt.matchesPassword(token, "$2a$10$hash-nuevo")).isFalse();
    }

    @Test void rejectsExpiredTokensWrongIssuerAndMalformedInput() {
        String expired=JWT.create().withSubject("ana").withIssuer(settings.issuer()).withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256(settings.secret()));
        String otherIssuer=JWT.create().withSubject("ana").withIssuer("other-issuer").withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256(settings.secret()));
        assertThat(jwt.isValid(expired)).isFalse();
        assertThat(jwt.isValid(otherIssuer)).isFalse();
        assertThat(jwt.isValid("bad-token")).isFalse();
        assertThat(jwt.isValid(null)).isFalse();
    }
}
