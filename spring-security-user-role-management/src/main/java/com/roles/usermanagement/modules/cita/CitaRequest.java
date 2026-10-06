package com.roles.usermanagement.modules.cita;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Datos de una cita. estado es opcional al crear (PROGRAMADA por defecto). */
public record CitaRequest(
        @NotNull Long pacienteId,
        @NotNull Long medicoId,
        @NotNull LocalDateTime fechaHora,
        @Size(max = 255) String motivo,
        EstadoCita estado) {}
