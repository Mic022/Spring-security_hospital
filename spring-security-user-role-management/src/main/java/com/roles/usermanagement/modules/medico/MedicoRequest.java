package com.roles.usermanagement.modules.medico;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos para crear o actualizar un médico. username es opcional: la cuenta con rol MEDICO que usará. */
public record MedicoRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 100) String especialidad,
        @Size(max = 50) String username) {}
