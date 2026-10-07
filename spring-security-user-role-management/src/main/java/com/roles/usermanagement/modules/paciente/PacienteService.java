package com.roles.usermanagement.modules.paciente;

import com.roles.usermanagement.modules.Paginas;
import com.roles.usermanagement.modules.ingreso.EstadoIngreso;
import com.roles.usermanagement.modules.ingreso.Ingreso;
import com.roles.usermanagement.modules.ingreso.IngresoRepository;
import com.roles.usermanagement.modules.ingreso.IngresoResponse;
import com.roles.usermanagement.modules.medico.AccesoMedico;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PacienteService {
    private final PacienteRepository pacientes;
    private final IngresoRepository ingresos;
    private final AccesoMedico acceso;

    public PacienteService(PacienteRepository pacientes, IngresoRepository ingresos, AccesoMedico acceso) {
        this.pacientes = pacientes;
        this.ingresos = ingresos;
        this.acceso = acceso;
    }

    /** Busca un paciente o responde 404. Lo usan también ingresos, citas y reportes. */
    public Paciente existente(Long id) {
        return pacientes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
    }

    /**
     * Respuesta con el ingreso más reciente del paciente. El ingreso es información clínica:
     * solo se incluye si la cuenta tiene PACIENTE_READ (recepción solo ve los datos personales).
     */
    private PacienteResponse dto(Paciente p) {
        IngresoResponse ultimo = !puedeVerClinico() ? null : ingresos.findFirstByPacienteIdOrderByFechaIngresoDesc(p.getId())
                .map(IngresoResponse::of).orElse(null);
        return new PacienteResponse(p.getId(), p.getNombre(), p.getDocumento(), p.getFechaNacimiento(), p.getTelefono(), ultimo);
    }

    /** ¿La cuenta actual puede ver información clínica (ingresos)? */
    private static boolean puedeVerClinico() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "PACIENTE_READ".equals(a.getAuthority()));
    }

    /**
     * Búsqueda con cualquier combinación de filtros (módulo de filtros).
     * Los filtros de ingreso se cumplen sobre un mismo ingreso del paciente.
     * Si el usuario es médico, solo aparecen sus pacientes.
     */
    @Transactional(readOnly = true)
    public Page<PacienteResponse> buscar(FiltroPacientes f, int page, int size) {
        Long medico = acceso.medicoActual().orElse(null);
        Specification<Paciente> spec = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (f.nombre() != null && !f.nombre().isBlank()) {
                condiciones.add(cb.like(cb.lower(root.get("nombre")), "%" + f.nombre().trim().toLowerCase() + "%"));
            }
            if (f.filtraIngresos()) {
                // exists (select i from Ingreso i where i.paciente = p and <filtros>)
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Ingreso> i = sub.from(Ingreso.class);
                List<Predicate> enIngreso = new ArrayList<>();
                enIngreso.add(cb.equal(i.get("paciente"), root));
                if (f.estado() != null) enIngreso.add(cb.equal(i.get("estado"), f.estado()));
                if (f.medico() != null) enIngreso.add(cb.equal(i.get("medico").get("id"), f.medico()));
                if (f.area() != null) enIngreso.add(cb.equal(i.get("area"), f.area()));
                if (f.ingresoDesde() != null) {
                    enIngreso.add(cb.greaterThanOrEqualTo(i.get("fechaIngreso"), f.ingresoDesde().atStartOfDay()));
                }
                if (f.ingresoHasta() != null) {
                    // Incluye todo el día "hasta".
                    enIngreso.add(cb.lessThan(i.get("fechaIngreso"), f.ingresoHasta().plusDays(1).atStartOfDay()));
                }
                if (f.recuperacionHasta() != null) {
                    // Próximos a recuperarse: fecha estimada hasta ese día y todavía no recuperados.
                    enIngreso.add(cb.lessThanOrEqualTo(i.get("fechaEstimadaRecuperacion"), f.recuperacionHasta()));
                    if (f.estado() == null) enIngreso.add(cb.notEqual(i.get("estado"), EstadoIngreso.RECUPERADO));
                }
                sub.select(i.get("id")).where(enIngreso.toArray(Predicate[]::new));
                condiciones.add(cb.exists(sub));
            }
            if (medico != null) condiciones.add(AccesoMedico.pacienteDe(root, medico, query, cb));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return pacientes.findAll(spec, Paginas.of(page, size, Sort.by("nombre"))).map(this::dto);
    }

    /** Busca por documento (p. ej. recepción, antes de registrar una cita). */
    @Transactional(readOnly = true)
    public PacienteResponse porDocumento(String documento) {
        Paciente p = pacientes.findByDocumento(documento.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
        acceso.verificarPaciente(p.getId());
        return dto(p);
    }

    public PacienteResponse crear(PacienteRequest request) {
        if (pacientes.findByDocumento(request.documento().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un paciente con ese documento");
        }
        Paciente p = new Paciente();
        aplicar(p, request);
        return dto(pacientes.save(p));
    }

    public PacienteResponse actualizar(Long id, PacienteRequest request) {
        Paciente p = existente(id);
        // Si a un médico se le concede PACIENTE_MANAGE, solo puede editar a sus pacientes.
        acceso.verificarPaciente(p.getId());
        if (pacientes.existsByDocumentoAndIdNot(request.documento().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un paciente con ese documento");
        }
        aplicar(p, request);
        return dto(p);
    }

    private void aplicar(Paciente p, PacienteRequest request) {
        p.setNombre(request.nombre().trim());
        p.setDocumento(request.documento().trim());
        p.setFechaNacimiento(request.fechaNacimiento());
        p.setTelefono(request.telefono());
    }
}
