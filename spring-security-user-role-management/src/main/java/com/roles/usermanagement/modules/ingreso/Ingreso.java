package com.roles.usermanagement.modules.ingreso;

import com.roles.usermanagement.modules.medico.Medico;
import com.roles.usermanagement.modules.paciente.Paciente;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Hospitalización de un paciente, con su médico responsable, área, habitación y estado. */
@Entity
@Table(name = "ingreso")
@Getter
@Setter
public class Ingreso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    /** Médico responsable de este ingreso (puede cambiar durante el ingreso). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_medico")
    private Medico medico;

    @Column(nullable = false)
    private LocalDateTime fechaIngreso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoIngreso estado;

    private LocalDate fechaEstimadaRecuperacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Area area;

    @Column(nullable = false, length = 20)
    private String habitacion;
}
