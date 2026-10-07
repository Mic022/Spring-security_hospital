package com.roles.usermanagement.modules.paciente;

import com.roles.usermanagement.modules.ingreso.IngresoResponse;
import java.time.LocalDate;

/** Paciente con su ingreso más reciente (null si nunca ha sido ingresado). */
public record PacienteResponse(Long id, String nombre, String documento, LocalDate fechaNacimiento, String telefono,
        IngresoResponse ultimoIngreso) {}
