package com.roles.usermanagement.modules.cita;

import java.time.LocalDateTime;

public record CitaResponse(Long id, Long pacienteId, String pacienteNombre, Long medicoId, String medicoNombre,
        LocalDateTime fechaHora, String motivo, EstadoCita estado) {

    public static CitaResponse of(Cita c) {
        return new CitaResponse(c.getId(), c.getPaciente().getId(), c.getPaciente().getNombre(),
                c.getMedico().getId(), c.getMedico().getNombre(), c.getFechaHora(), c.getMotivo(), c.getEstado());
    }
}
