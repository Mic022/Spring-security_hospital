package com.roles.usermanagement.modules.cita;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CitaRepository extends JpaRepository<Cita, Long>, JpaSpecificationExecutor<Cita> {
    /** Citas de un paciente en orden cronológico (para el reporte). */
    List<Cita> findByPacienteIdOrderByFechaHoraAsc(Long pacienteId);
}
