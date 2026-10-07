package com.roles.usermanagement.modules.ingreso;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Cambios de un ingreso. Todos los campos son opcionales: los que lleguen en null no cambian. */
public record IngresoUpdateRequest(
        EstadoIngreso estado,
        Long medicoId,
        Area area,
        @Size(min = 1, max = 20) String habitacion,
        LocalDate fechaEstimadaRecuperacion) {}
