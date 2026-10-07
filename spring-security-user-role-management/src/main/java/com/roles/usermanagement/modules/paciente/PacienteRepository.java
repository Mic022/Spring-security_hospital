package com.roles.usermanagement.modules.paciente;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JpaSpecificationExecutor permite combinar los filtros opcionales de la búsqueda. */
public interface PacienteRepository extends JpaRepository<Paciente, Long>, JpaSpecificationExecutor<Paciente> {

    Optional<Paciente> findByDocumento(String documento);

    boolean existsByDocumentoAndIdNot(String documento, Long id);

    /** "Sus pacientes": el paciente tiene al menos un ingreso o una cita con ese médico. */
    @Query("""
            select count(p) > 0 from Paciente p where p.id = :paciente and (
              exists (select 1 from Ingreso i where i.paciente = p and i.medico.id = :medico) or
              exists (select 1 from Cita c where c.paciente = p and c.medico.id = :medico))
            """)
    boolean esPacienteDe(@Param("paciente") Long paciente, @Param("medico") Long medico);
}
