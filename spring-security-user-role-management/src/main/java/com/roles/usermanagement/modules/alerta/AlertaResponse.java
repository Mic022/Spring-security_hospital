package com.roles.usermanagement.modules.alerta;

import java.time.LocalDateTime;

public record AlertaResponse(Long id, Long pacienteId, String pacienteNombre, TipoAlerta tipo, String mensaje,
        LocalDateTime fecha, EstadoAlerta estado, String usernameAtiende) {

    public static AlertaResponse of(Alerta a) {
        return new AlertaResponse(a.getId(), a.getPaciente().getId(), a.getPaciente().getNombre(), a.getTipo(),
                a.getMensaje(), a.getFecha(), a.getEstado(), a.getUsernameAtiende());
    }
}
