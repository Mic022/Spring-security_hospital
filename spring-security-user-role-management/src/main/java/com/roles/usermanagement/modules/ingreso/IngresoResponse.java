package com.roles.usermanagement.modules.ingreso;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record IngresoResponse(Long id, Long pacienteId, String pacienteNombre, Long medicoId, String medicoNombre,
        LocalDateTime fechaIngreso, EstadoIngreso estado, LocalDate fechaEstimadaRecuperacion, Area area, String habitacion) {

    public static IngresoResponse of(Ingreso i) {
        return new IngresoResponse(i.getId(), i.getPaciente().getId(), i.getPaciente().getNombre(),
                i.getMedico().getId(), i.getMedico().getNombre(), i.getFechaIngreso(), i.getEstado(),
                i.getFechaEstimadaRecuperacion(), i.getArea(), i.getHabitacion());
    }
}
