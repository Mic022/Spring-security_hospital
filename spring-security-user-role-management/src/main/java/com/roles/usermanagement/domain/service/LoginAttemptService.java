package com.roles.usermanagement.domain.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Limita los intentos de login contra fuerza bruta. Tras app.login.max-intentos fallos seguidos,
 * la combinación usuario + IP queda bloqueada durante app.login.bloqueo.
 * Se cuenta por usuario + IP para que un atacante no pueda bloquear la cuenta de otro desde su equipo.
 * Los contadores viven en memoria: se reinician al reiniciar la aplicación.
 */
@Service
public class LoginAttemptService {
    private final int maxIntentos;
    private final Duration bloqueo;
    private final Clock clock;
    private final Map<String, Intentos> intentos = new ConcurrentHashMap<>();

    /** Fallos acumulados y, si se alcanzó el máximo, hasta cuándo dura el bloqueo. */
    private record Intentos(int fallos, Instant bloqueadoHasta) {}

    public LoginAttemptService(@Value("${app.login.max-intentos:5}") int maxIntentos,
                               @Value("${app.login.bloqueo:15m}") Duration bloqueo,
                               Clock clock) {
        this.maxIntentos = maxIntentos;
        this.bloqueo = bloqueo;
        this.clock = clock;
    }

    /** Clave de conteo: usuario (sin distinguir mayúsculas) + IP. */
    public static String clave(String username, String ip) {
        return username.trim().toLowerCase() + "|" + ip;
    }

    /** Minutos que faltan para poder intentar de nuevo; 0 si no está bloqueado. */
    public long minutosBloqueado(String clave) {
        Intentos actual = intentos.get(clave);
        if (actual == null || actual.bloqueadoHasta() == null) return 0;
        Duration resta = Duration.between(Instant.now(clock), actual.bloqueadoHasta());
        if (resta.isNegative() || resta.isZero()) {
            intentos.remove(clave); // el bloqueo ya venció: empieza de cero
            return 0;
        }
        return Math.max(1, (resta.toSeconds() + 59) / 60); // redondea hacia arriba
    }

    /** Registra un fallo; al llegar al máximo, bloquea. */
    public void fallo(String clave) {
        if (intentos.size() > 10_000) {
            // Evita que la memoria crezca sin límite con usuarios inventados: se descartan los no bloqueados.
            intentos.values().removeIf(i -> i.bloqueadoHasta() == null);
        }
        intentos.compute(clave, (k, actual) -> {
            int fallos = (actual == null ? 0 : actual.fallos()) + 1;
            Instant hasta = fallos >= maxIntentos ? Instant.now(clock).plus(bloqueo) : null;
            return new Intentos(fallos, hasta);
        });
    }

    /** Un login correcto reinicia el contador. */
    public void exito(String clave) {
        intentos.remove(clave);
    }
}
