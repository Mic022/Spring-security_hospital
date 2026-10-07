package com.roles.usermanagement.domain.service;

import java.util.EnumSet;
import java.util.Set;

/** Roles y permisos base del sistema. El bootstrap los crea al arrancar si no existen. */
public class UserRoles {

    /** Permisos disponibles. Cada endpoint exige uno con @PreAuthorize("hasAuthority('...')"). */
    public enum Authority {
        // Administración de cuentas, roles y permisos
        USER_READ, USER_CREATE, USER_UPDATE, USER_DELETE, ROLE_ASSIGN, PERMISSION_ASSIGN, ROLE_MANAGE, PERMISSION_MANAGE,
        // Base del hospital: READ consulta, MANAGE crea y modifica
        MEDICO_READ, MEDICO_MANAGE,
        PACIENTE_READ, PACIENTE_MANAGE,
        INGRESO_MANAGE,
        CITA_READ, CITA_MANAGE,
        // Módulos de reportes y alertas
        REPORTE_READ, ALERTA_READ, ALERTA_ATENDER;

        public String value() {
            return name();
        }
    }

    /** Roles del hospital con sus permisos base (tabla de la sección 4 del documento). */
    public enum Role {
        /** Acceso total. */
        ADMIN(EnumSet.allOf(Authority.class)),
        /** Solo ve sus pacientes: la restricción se aplica en AccesoMedico. */
        MEDICO(EnumSet.of(Authority.MEDICO_READ, Authority.PACIENTE_READ, Authority.INGRESO_MANAGE,
                Authority.CITA_READ, Authority.REPORTE_READ, Authority.ALERTA_READ, Authority.ALERTA_ATENDER)),
        /** Alertas y filtros de pacientes; sin reportes ni citas. */
        ENFERMERO(EnumSet.of(Authority.MEDICO_READ, Authority.PACIENTE_READ,
                Authority.ALERTA_READ, Authority.ALERTA_ATENDER)),
        /** Citas; registra pacientes, pero no consulta su información clínica. */
        RECEPCION(EnumSet.of(Authority.MEDICO_READ, Authority.PACIENTE_MANAGE,
                Authority.CITA_READ, Authority.CITA_MANAGE));

        private final Set<Authority> permissions;

        Role(Set<Authority> permissions) {
            this.permissions = permissions;
        }

        public Set<Authority> permissions() {
            return permissions;
        }
    }
}
