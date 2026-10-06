package com.roles.usermanagement.modules.paciente;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Datos personales del paciente. La información clínica vive en sus ingresos y citas. */
@Entity
@Table(name = "paciente")
@Getter
@Setter
public class Paciente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Documento de identidad; no se repite. */
    @Column(nullable = false, length = 30, unique = true)
    private String documento;

    private LocalDate fechaNacimiento;

    @Column(length = 30)
    private String telefono;
}
