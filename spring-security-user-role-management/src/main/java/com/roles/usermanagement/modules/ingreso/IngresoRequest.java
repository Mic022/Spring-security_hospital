package com.roles.usermanagement.modules.ingreso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Datos para registrar un ingreso. El estado inicial siempre es INGRESADO. */
public record IngresoRequest(
        @NotNull Long pacienteId,
        @NotNull Long medicoId,
        @NotNull Area area,
        @NotBlank @Size(max = 20) String habitacion,
        LocalDate fechaEstimadaRecuperacion) {}
