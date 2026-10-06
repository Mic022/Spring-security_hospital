package com.roles.usermanagement.modules.alerta;

import com.roles.usermanagement.modules.Paginas;
import com.roles.usermanagement.modules.medico.AccesoMedico;
import com.roles.usermanagement.modules.paciente.Paciente;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AlertaService {
    private final AlertaRepository alertas;
    private final AccesoMedico acceso;
    private final Clock clock;

    public AlertaService(AlertaRepository alertas, AccesoMedico acceso, Clock clock) {
        this.alertas = alertas;
        this.acceso = acceso;
        this.clock = clock;
    }

    /**
     * Crea una alerta pendiente. Se llama desde los servicios de ingresos y citas, dentro de su
     * transacción: si el cambio o la alerta fallan, no se guarda ninguno de los dos.
     */
    public void registrar(Paciente paciente, TipoAlerta tipo, String mensaje) {
        Alerta alerta = new Alerta();
        alerta.setPaciente(paciente);
        alerta.setTipo(tipo);
        alerta.setMensaje(mensaje.length() > 255 ? mensaje.substring(0, 252) + "..." : mensaje);
        alerta.setFecha(LocalDateTime.now(clock));
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alertas.save(alerta);
    }

    /** Listado de alertas, las más recientes primero; estado es opcional. El médico solo ve las de sus pacientes. */
    @Transactional(readOnly = true)
    public Page<AlertaResponse> buscar(EstadoAlerta estado, int page, int size) {
        Long medico = acceso.medicoActual().orElse(null);
        Specification<Alerta> spec = (root, query, cb) -> cb.and(
                estado == null ? cb.conjunction() : cb.equal(root.get("estado"), estado),
                medico == null ? cb.conjunction() : AccesoMedico.pacienteDe(root.get("paciente"), medico, query, cb));
        return alertas.findAll(spec, Paginas.of(page, size, Sort.by("fecha").descending().and(Sort.by("id").descending())))
                .map(AlertaResponse::of);
    }

    /** Marca la alerta como atendida por el usuario actual. Repetirlo no cambia quién la atendió. */
    public AlertaResponse atender(Long id, String username) {
        Alerta alerta = alertas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta no encontrada"));
        acceso.verificarPaciente(alerta.getPaciente().getId());
        if (alerta.getEstado() == EstadoAlerta.PENDIENTE) {
            alerta.setEstado(EstadoAlerta.ATENDIDA);
            alerta.setUsernameAtiende(username);
        }
        return AlertaResponse.of(alerta);
    }
}
