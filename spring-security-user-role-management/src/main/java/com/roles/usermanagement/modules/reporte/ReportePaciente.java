package com.roles.usermanagement.modules.reporte;

import com.roles.usermanagement.modules.cita.CitaResponse;
import com.roles.usermanagement.modules.ingreso.Area;
import com.roles.usermanagement.modules.ingreso.EstadoIngreso;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reporte de un paciente (sección 2.1 del documento). Se arma en el momento y no se guarda.
 * Los datos del ingreso son del más reciente; quedan en null si el paciente nunca fue ingresado.
 */
public record ReportePaciente(
        Long pacienteId,
        String nombre,
        String documento,
        LocalDateTime fechaIngreso,
        String medicoResponsable,
        Area area,
        String habitacion,
        EstadoIngreso estado,
        LocalDate fechaEstimadaRecuperacion,
        /** Días desde el ingreso hasta hoy. */
        Long diasTranscurridos,
        /** Días hasta la fecha estimada; negativo si ya pasó. null si no hay fecha estimada. */
        Long diasRestantes,
        List<CitaResponse> citas,
        List<Cambio> historial) {

    /** Una fila del historial de estados. */
    public record Cambio(LocalDateTime fecha, EstadoIngreso estado, String detalle, String username) {}
}
