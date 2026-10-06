package com.roles.usermanagement.modules.medico;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicoRepository extends JpaRepository<Medico, Long> {
    /** Médico vinculado a una cuenta de usuario (para saber quién es el médico del token). */
    Optional<Medico> findByUsuarioUsername(String username);
}
