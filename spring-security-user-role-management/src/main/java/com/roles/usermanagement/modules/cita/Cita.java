package com.roles.usermanagement.modules.cita;

import com.roles.usermanagement.modules.medico.Medico;
import com.roles.usermanagement.modules.paciente.Paciente;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Cita de un paciente con un médico. */
@Entity
@Table(name = "cita")
@Getter
@Setter
public class Cita {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_medico")
    private Medico medico;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(length = 255)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCita estado;
}
