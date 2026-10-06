package com.roles.usermanagement.modules.medico;

import com.roles.usermanagement.persistance.entity.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Médico del hospital. Se asigna en cada ingreso y en cada cita, no directamente al paciente. */
@Entity
@Table(name = "medico")
@Getter
@Setter
public class Medico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String especialidad;

    /**
     * Cuenta con la que inicia sesión este médico (opcional, una cuenta por médico).
     * Es el vínculo que permite saber, a partir del token, qué pacientes puede ver.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "username", unique = true)
    private UserEntity usuario;
}
