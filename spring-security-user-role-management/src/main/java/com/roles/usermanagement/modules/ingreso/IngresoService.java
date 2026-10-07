package com.roles.usermanagement.modules.ingreso;

import com.roles.usermanagement.modules.alerta.AlertaService;
import com.roles.usermanagement.modules.alerta.TipoAlerta;
import com.roles.usermanagement.modules.medico.AccesoMedico;
import com.roles.usermanagement.modules.medico.Medico;
import com.roles.usermanagement.modules.medico.MedicoService;
import com.roles.usermanagement.modules.paciente.Paciente;
import com.roles.usermanagement.modules.paciente.PacienteService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registro y cambios de ingresos. Cada cambio guarda su historial y su alerta en la misma
 * transacción (@Transactional): si algo falla, no se guarda nada.
 */
@Service
@Transactional
public class IngresoService {
    private final IngresoRepository ingresos;
    private final HistorialEstadoRepository historial;
    private final PacienteService pacientes;
    private final MedicoService medicos;
    private final AlertaService alertas;
    private final AccesoMedico acceso;
    private final Clock clock;

    public IngresoService(IngresoRepository ingresos, HistorialEstadoRepository historial, PacienteService pacientes,
                          MedicoService medicos, AlertaService alertas, AccesoMedico acceso, Clock clock) {
        this.ingresos = ingresos;
        this.historial = historial;
        this.pacientes = pacientes;
        this.medicos = medicos;
        this.alertas = alertas;
        this.acceso = acceso;
        this.clock = clock;
    }

    /** Registra un ingreso nuevo en estado INGRESADO. Un paciente solo puede tener un ingreso abierto. */
    public IngresoResponse crear(IngresoRequest request, String username) {
        acceso.verificarMedico(request.medicoId()); // un médico solo puede ingresar pacientes a su nombre
        Paciente paciente = pacientes.existente(request.pacienteId());
        // ...y solo a pacientes que ya son suyos (p. ej. con una cita agendada por recepción).
        // Sin esto, crear un ingreso le daría acceso a cualquier paciente. Va antes del 409 para no
        // revelar si un paciente ajeno tiene un ingreso abierto.
        acceso.verificarPaciente(paciente.getId());
        if (ingresos.existsByPacienteIdAndEstadoNot(paciente.getId(), EstadoIngreso.RECUPERADO)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El paciente ya tiene un ingreso abierto");
        }
        Ingreso ingreso = new Ingreso();
        ingreso.setPaciente(paciente);
        ingreso.setMedico(medicos.existente(request.medicoId()));
        ingreso.setFechaIngreso(LocalDateTime.now(clock));
        ingreso.setEstado(EstadoIngreso.INGRESADO);
        ingreso.setArea(request.area());
        ingreso.setHabitacion(request.habitacion().trim());
        ingreso.setFechaEstimadaRecuperacion(request.fechaEstimadaRecuperacion());
        ingresos.save(ingreso);
        guardarHistorial(ingreso, "Ingreso registrado", username);
        return IngresoResponse.of(ingreso);
    }

    /**
     * Aplica los campos que lleguen (los null no cambian). Si cambian estado, médico, área o habitación,
     * se guarda el historial y se genera una alerta (RECUPERADO o CAMBIO_ESTADO).
     */
    public IngresoResponse actualizar(Long id, IngresoUpdateRequest request, String username) {
        Ingreso ingreso = ingresos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingreso no encontrado"));
        acceso.verificarMedico(ingreso.getMedico().getId()); // el médico solo modifica ingresos a su cargo
        if (ingreso.getEstado() == EstadoIngreso.RECUPERADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El ingreso ya terminó: el paciente está recuperado");
        }

        // Se arma la lista de cambios para el historial y el mensaje de la alerta.
        List<String> cambios = new ArrayList<>();
        if (request.estado() != null && request.estado() != ingreso.getEstado()) {
            cambios.add("Estado: " + ingreso.getEstado() + " → " + request.estado());
            ingreso.setEstado(request.estado());
        }
        if (request.medicoId() != null && !request.medicoId().equals(ingreso.getMedico().getId())) {
            Medico nuevo = medicos.existente(request.medicoId());
            cambios.add("Médico: " + ingreso.getMedico().getNombre() + " → " + nuevo.getNombre());
            ingreso.setMedico(nuevo);
        }
        if (request.area() != null && request.area() != ingreso.getArea()) {
            cambios.add("Área: " + ingreso.getArea() + " → " + request.area());
            ingreso.setArea(request.area());
        }
        if (request.habitacion() != null && !request.habitacion().trim().equals(ingreso.getHabitacion())) {
            cambios.add("Habitación: " + ingreso.getHabitacion() + " → " + request.habitacion().trim());
            ingreso.setHabitacion(request.habitacion().trim());
        }
        // La fecha estimada se guarda, pero no genera alerta.
        if (request.fechaEstimadaRecuperacion() != null
                && !Objects.equals(request.fechaEstimadaRecuperacion(), ingreso.getFechaEstimadaRecuperacion())) {
            ingreso.setFechaEstimadaRecuperacion(request.fechaEstimadaRecuperacion());
        }

        if (!cambios.isEmpty()) {
            String detalle = String.join("; ", cambios);
            guardarHistorial(ingreso, detalle, username);
            TipoAlerta tipo = ingreso.getEstado() == EstadoIngreso.RECUPERADO ? TipoAlerta.RECUPERADO : TipoAlerta.CAMBIO_ESTADO;
            alertas.registrar(ingreso.getPaciente(), tipo, ingreso.getPaciente().getNombre() + ": " + detalle);
        }
        return IngresoResponse.of(ingreso);
    }

    /** Agrega una fila al historial con el estado actual del ingreso. */
    private void guardarHistorial(Ingreso ingreso, String detalle, String username) {
        HistorialEstado h = new HistorialEstado();
        h.setIngreso(ingreso);
        h.setEstado(ingreso.getEstado());
        h.setDetalle(detalle.length() > 255 ? detalle.substring(0, 252) + "..." : detalle);
        h.setFechaCambio(LocalDateTime.now(clock));
        h.setUsername(username);
        historial.save(h);
    }
}
