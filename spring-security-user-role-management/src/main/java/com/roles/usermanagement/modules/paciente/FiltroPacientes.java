package com.roles.usermanagement.modules.paciente;

import com.roles.usermanagement.modules.ingreso.Area;
import com.roles.usermanagement.modules.ingreso.EstadoIngreso;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Parámetros opcionales de GET /api/pacientes (todos se pueden combinar).
 * Fechas en formato AAAA-MM-DD.
 */
public record FiltroPacientes(
        String nombre,
        EstadoIngreso estado,
        Long medico,
        Area area,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ingresoDesde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ingresoHasta,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recuperacionHasta) {

    /** ¿Hay algún filtro sobre los ingresos? */
    boolean filtraIngresos() {
        return estado != null || medico != null || area != null
                || ingresoDesde != null || ingresoHasta != null || recuperacionHasta != null;
    }
}
