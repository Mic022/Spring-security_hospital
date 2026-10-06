package com.roles.usermanagement.modules.medico;

public record MedicoResponse(Long id, String nombre, String especialidad, String username) {

    /** Convierte la entidad en la respuesta de la API. */
    public static MedicoResponse of(Medico m) {
        return new MedicoResponse(m.getId(), m.getNombre(), m.getEspecialidad(),
                m.getUsuario() == null ? null : m.getUsuario().getUsername());
    }
}
