package com.roles.usermanagement.modules.paciente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PacienteRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 30) String documento,
        @Past LocalDate fechaNacimiento,
        @Size(max = 30) String telefono) {}
