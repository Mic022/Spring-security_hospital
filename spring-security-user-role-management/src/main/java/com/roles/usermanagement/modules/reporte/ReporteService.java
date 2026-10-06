package com.roles.usermanagement.modules.reporte;

import com.roles.usermanagement.modules.cita.CitaRepository;
import com.roles.usermanagement.modules.cita.CitaResponse;
import com.roles.usermanagement.modules.ingreso.HistorialEstadoRepository;
import com.roles.usermanagement.modules.ingreso.Ingreso;
import com.roles.usermanagement.modules.ingreso.IngresoRepository;
import com.roles.usermanagement.modules.medico.AccesoMedico;
import com.roles.usermanagement.modules.paciente.Paciente;
import com.roles.usermanagement.modules.paciente.PacienteService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Arma el reporte del paciente a partir de las tablas existentes (no tiene tabla propia). */
@Service
@Transactional(readOnly = true)
public class ReporteService {
    private final PacienteService pacientes;
    private final IngresoRepository ingresos;
    private final HistorialEstadoRepository historial;
    private final CitaRepository citas;
    private final AccesoMedico acceso;
    private final Clock clock;

    public ReporteService(PacienteService pacientes, IngresoRepository ingresos, HistorialEstadoRepository historial,
                          CitaRepository citas, AccesoMedico acceso, Clock clock) {
        this.pacientes = pacientes;
        this.ingresos = ingresos;
        this.historial = historial;
        this.citas = citas;
        this.acceso = acceso;
        this.clock = clock;
    }

    public ReportePaciente reporte(Long pacienteId) {
        Paciente p = pacientes.existente(pacienteId);
        acceso.verificarPaciente(p.getId()); // el médico solo ve reportes de sus pacientes

        List<CitaResponse> citasPaciente = citas.findByPacienteIdOrderByFechaHoraAsc(p.getId())
                .stream().map(CitaResponse::of).toList();
        Ingreso i = ingresos.findFirstByPacienteIdOrderByFechaIngresoDesc(p.getId()).orElse(null);
        if (i == null) {
            return new ReportePaciente(p.getId(), p.getNombre(), p.getDocumento(),
                    null, null, null, null, null, null, null, null, citasPaciente, List.of());
        }

        // Los días se calculan al pedir el reporte porque cambian a diario.
        LocalDate hoy = LocalDate.now(clock);
        long transcurridos = ChronoUnit.DAYS.between(i.getFechaIngreso().toLocalDate(), hoy);
        Long restantes = i.getFechaEstimadaRecuperacion() == null ? null
                : ChronoUnit.DAYS.between(hoy, i.getFechaEstimadaRecuperacion());

        List<ReportePaciente.Cambio> cambios = historial.findByIngresoIdOrderByFechaCambioAscIdAsc(i.getId()).stream()
                .map(h -> new ReportePaciente.Cambio(h.getFechaCambio(), h.getEstado(), h.getDetalle(), h.getUsername()))
                .toList();

        return new ReportePaciente(p.getId(), p.getNombre(), p.getDocumento(), i.getFechaIngreso(),
                i.getMedico().getNombre(), i.getArea(), i.getHabitacion(), i.getEstado(),
                i.getFechaEstimadaRecuperacion(), transcurridos, restantes, citasPaciente, cambios);
    }
}
