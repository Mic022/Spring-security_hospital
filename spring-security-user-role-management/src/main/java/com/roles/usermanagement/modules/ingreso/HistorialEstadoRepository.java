package com.roles.usermanagement.modules.ingreso;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialEstadoRepository extends JpaRepository<HistorialEstado, Long> {
    /** Historial de un ingreso en orden cronológico. */
    List<HistorialEstado> findByIngresoIdOrderByFechaCambioAscIdAsc(Long ingresoId);
}
