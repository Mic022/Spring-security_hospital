package com.roles.usermanagement.modules.medico;

import com.roles.usermanagement.modules.cita.Cita;
import com.roles.usermanagement.modules.ingreso.Ingreso;
import com.roles.usermanagement.modules.paciente.Paciente;
import com.roles.usermanagement.modules.paciente.PacienteRepository;
import jakarta.persistence.criteria.*;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regla "el médico solo ve a sus pacientes", en un único lugar para todos los módulos.
 * Sus pacientes son los que tienen al menos un ingreso o una cita con él (también los antiguos).
 * Los demás roles (ADMIN, ENFERMERO, RECEPCION) no tienen esta restricción.
 */
@Component
public class AccesoMedico {
    private final MedicoRepository medicos;
    private final PacienteRepository pacientes;

    public AccesoMedico(MedicoRepository medicos, PacienteRepository pacientes) {
        this.medicos = medicos;
        this.pacientes = pacientes;
    }

    /**
     * Id del médico si el usuario actual tiene el rol MEDICO; vacío si no se aplica restricción.
     * Una cuenta MEDICO sin médico vinculado no puede ver nada (403).
     */
    public Optional<Long> medicoActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esMedico = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MEDICO".equals(a.getAuthority()));
        if (!esMedico) return Optional.empty();
        Long id = medicos.findByUsuarioUsername(auth.getName()).map(Medico::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Tu cuenta MEDICO no está vinculada a ningún médico"));
        return Optional.of(id);
    }

    /** Falla con 403 si el usuario es médico y el paciente no es suyo. */
    public void verificarPaciente(Long pacienteId) {
        medicoActual().ifPresent(medico -> {
            if (!pacientes.esPacienteDe(pacienteId, medico)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes ver información de tus pacientes");
            }
        });
    }

    /** Falla con 403 si el usuario es médico y el registro está a nombre de otro médico. */
    public void verificarMedico(Long medicoId) {
        medicoActual().ifPresent(medico -> {
            if (!Objects.equals(medico, medicoId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes gestionar registros a tu nombre");
            }
        });
    }

    /**
     * Condición JPA para las búsquedas: el paciente indicado tiene algún ingreso o cita con el médico.
     * Se usa dentro de las Specification de pacientes, citas y alertas.
     */
    public static Predicate pacienteDe(Path<Paciente> paciente, Long medico, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> ingresos = query.subquery(Long.class);
        Root<Ingreso> i = ingresos.from(Ingreso.class);
        ingresos.select(i.get("id")).where(cb.equal(i.get("paciente"), paciente), cb.equal(i.get("medico").get("id"), medico));

        Subquery<Long> citas = query.subquery(Long.class);
        Root<Cita> c = citas.from(Cita.class);
        citas.select(c.get("id")).where(cb.equal(c.get("paciente"), paciente), cb.equal(c.get("medico").get("id"), medico));

        return cb.or(cb.exists(ingresos), cb.exists(citas));
    }
}
