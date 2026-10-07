package com.roles.usermanagement.web.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Firma y verifica los tokens usando la configuración security.jwt. */
@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtUtil {
    private final JwtProperties properties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtUtil(JwtProperties properties) {
        this.properties=properties;
        this.algorithm=Algorithm.HMAC256(properties.secret());
        this.verifier=JWT.require(algorithm).withIssuer(properties.issuer()).build();
    }

    /** Nombre del claim con la huella de la contraseña. */
    private static final String PASSWORD_CLAIM="pwd";

    /**
     * Crea el token. Incluye una huella de la contraseña actual (no la contraseña ni su hash):
     * si la contraseña cambia, la huella deja de coincidir y los tokens anteriores dejan de servir.
     */
    public String create(String username, String passwordHash) {
        Instant now=Instant.now();
        return JWT.create().withSubject(username).withIssuer(properties.issuer())
                .withClaim(PASSWORD_CLAIM, fingerprint(passwordHash))
                .withIssuedAt(now).withExpiresAt(now.plus(properties.expiration())).sign(algorithm);
    }

    /** ¿El token se emitió con la contraseña actual de la cuenta? */
    public boolean matchesPassword(String token, String passwordHash) {
        String claim=verifier.verify(token).getClaim(PASSWORD_CLAIM).asString();
        return claim!=null && claim.equals(fingerprint(passwordHash));
    }

    /** SHA-256 del hash BCrypt, recortado: identifica la contraseña sin revelar nada útil de ella. */
    private static String fingerprint(String passwordHash) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public boolean isValid(String token) {
        if(token==null || token.isBlank()) return false;
        try {
            verifier.verify(token);
            return true;
        } catch(JWTVerificationException exception) {
            return false;
        }
    }

    public String getUsername(String token) {
        return verifier.verify(token).getSubject();
    }
}
