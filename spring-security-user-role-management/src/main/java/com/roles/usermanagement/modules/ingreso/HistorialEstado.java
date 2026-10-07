package com.roles.usermanagement.modules.ingreso;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Registro de cada cambio de un ingreso: estado resultante, qué cambió, cuándo y quién. */
@Entity
@Table(name = "historial_estado")
@Getter
@Setter
public class HistorialEstado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_ingreso")
    private Ingreso ingreso;

    /** Estado del ingreso después del cambio. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoIngreso estado;

    /** Descripción del cambio, p. ej. "Estado: INGRESADO → EN_TRATAMIENTO; Habitación: 101 → 205". */
    @Column(nullable = false, length = 255)
    private String detalle;

    @Column(nullable = false)
    private LocalDateTime fechaCambio;

    /** Usuario que hizo el cambio. */
    @Column(nullable = false, length = 50)
    private String username;
}
