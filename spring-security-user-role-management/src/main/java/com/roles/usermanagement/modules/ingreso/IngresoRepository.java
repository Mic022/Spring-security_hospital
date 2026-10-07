package com.roles.usermanagement.modules.ingreso;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngresoRepository extends JpaRepository<Ingreso, Long> {
    /** Ingreso más reciente del paciente (el que muestran el listado y el reporte). */
    Optional<Ingreso> findFirstByPacienteIdOrderByFechaIngresoDesc(Long pacienteId);

    /** ¿El paciente ya tiene un ingreso abierto (no recuperado)? */
    boolean existsByPacienteIdAndEstadoNot(Long pacienteId, EstadoIngreso estado);
}
