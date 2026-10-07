package com.roles.usermanagement.modules.alerta;

import com.roles.usermanagement.modules.paciente.Paciente;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Aviso automático sobre un paciente. No copia sus datos: solo guarda la referencia y un mensaje corto. */
@Entity
@Table(name = "alerta")
@Getter
@Setter
public class Alerta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAlerta tipo;

    @Column(nullable = false, length = 255)
    private String mensaje;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAlerta estado;

    /** Usuario que la marcó como atendida (null mientras está pendiente). */
    @Column(length = 50)
    private String usernameAtiende;
}
