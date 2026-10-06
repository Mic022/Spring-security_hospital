package com.roles.usermanagement.modules.cita;

import com.roles.usermanagement.modules.Paginas;
import com.roles.usermanagement.modules.alerta.AlertaService;
import com.roles.usermanagement.modules.alerta.TipoAlerta;
import com.roles.usermanagement.modules.medico.AccesoMedico;
import com.roles.usermanagement.modules.medico.MedicoService;
import com.roles.usermanagement.modules.paciente.PacienteService;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Citas. Crear o modificar una cita genera una alerta CITA en la misma transacción. */
@Service
@Transactional
public class CitaService {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CitaRepository citas;
    private final PacienteService pacientes;
    private final MedicoService medicos;
    private final AlertaService alertas;
    private final AccesoMedico acceso;

    public CitaService(CitaRepository citas, PacienteService pacientes, MedicoService medicos,
                       AlertaService alertas, AccesoMedico acceso) {
        this.citas = citas;
        this.pacientes = pacientes;
        this.medicos = medicos;
        this.alertas = alertas;
        this.acceso = acceso;
    }

    /** Búsqueda por fecha (un día) y/o médico, en orden cronológico. El médico solo ve citas de sus pacientes. */
    @Transactional(readOnly = true)
    public Page<CitaResponse> buscar(LocalDate fecha, Long medicoId, int page, int size) {
        Long medico = acceso.medicoActual().orElse(null);
        Specification<Cita> spec = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (fecha != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), fecha.atStartOfDay()));
                condiciones.add(cb.lessThan(root.get("fechaHora"), fecha.plusDays(1).atStartOfDay()));
            }
            if (medicoId != null) condiciones.add(cb.equal(root.get("medico").get("id"), medicoId));
            if (medico != null) condiciones.add(AccesoMedico.pacienteDe(root.get("paciente"), medico, query, cb));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return citas.findAll(spec, Paginas.of(page, size, Sort.by("fechaHora"))).map(CitaResponse::of);
    }

    public CitaResponse crear(CitaRequest request) {
        Cita cita = new Cita();
        aplicar(cita, request);
        citas.save(cita);
        alertas.registrar(cita.getPaciente(), TipoAlerta.CITA, "Nueva cita de " + cita.getPaciente().getNombre()
                + " el " + cita.getFechaHora().format(FORMATO) + " con " + cita.getMedico().getNombre());
        return CitaResponse.of(cita);
    }

    public CitaResponse actualizar(Long id, CitaRequest request) {
        Cita cita = citas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada"));
        acceso.verificarMedico(cita.getMedico().getId());
        aplicar(cita, request);
        alertas.registrar(cita.getPaciente(), TipoAlerta.CITA, "Cita modificada de " + cita.getPaciente().getNombre()
                + ": " + cita.getFechaHora().format(FORMATO) + " con " + cita.getMedico().getNombre() + " (" + cita.getEstado() + ")");
        return CitaResponse.of(cita);
    }

    /** Copia los datos del request; si el usuario es médico, la cita debe quedar a su nombre. */
    private void aplicar(Cita cita, CitaRequest request) {
        acceso.verificarMedico(request.medicoId());
        cita.setPaciente(pacientes.existente(request.pacienteId()));
        cita.setMedico(medicos.existente(request.medicoId()));
        cita.setFechaHora(request.fechaHora());
        cita.setMotivo(request.motivo());
        cita.setEstado(request.estado() != null ? request.estado()
                : cita.getEstado() != null ? cita.getEstado() : EstadoCita.PROGRAMADA);
    }
}
