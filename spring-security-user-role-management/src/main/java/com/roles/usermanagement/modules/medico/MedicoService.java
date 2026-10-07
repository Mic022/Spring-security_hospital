package com.roles.usermanagement.modules.medico;

import com.roles.usermanagement.domain.service.UserRoles;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import com.roles.usermanagement.persistance.entity.UserEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MedicoService {
    private final MedicoRepository medicos;
    private final UserCrudRepository users;

    public MedicoService(MedicoRepository medicos, UserCrudRepository users) {
        this.medicos = medicos;
        this.users = users;
    }

    /** Busca un médico o responde 404. Lo usan también ingresos y citas. */
    public Medico existente(Long id) {
        return medicos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<MedicoResponse> todos() {
        return medicos.findAll(Sort.by("nombre")).stream().map(MedicoResponse::of).toList();
    }

    public MedicoResponse crear(MedicoRequest request) {
        Medico medico = new Medico();
        aplicar(medico, request);
        return MedicoResponse.of(medicos.save(medico));
    }

    public MedicoResponse actualizar(Long id, MedicoRequest request) {
        Medico medico = existente(id);
        aplicar(medico, request);
        return MedicoResponse.of(medico);
    }

    /** Copia los datos del request a la entidad y valida la cuenta vinculada. */
    private void aplicar(Medico medico, MedicoRequest request) {
        medico.setNombre(request.nombre().trim());
        medico.setEspecialidad(request.especialidad().trim());
        medico.setUsuario(cuenta(request.username(), medico.getId()));
    }

    /** La cuenta debe existir, tener el rol MEDICO y no estar vinculada a otro médico. */
    private UserEntity cuenta(String username, Long medicoId) {
        if (username == null || username.isBlank()) return null;
        UserEntity user = users.findById(username.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta no existe"));
        boolean esMedico = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> UserRoles.Role.MEDICO.name().equals(r.getRole()));
        if (!esMedico) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta debe tener el rol MEDICO");
        medicos.findByUsuarioUsername(user.getUsername())
                .filter(otro -> !otro.getId().equals(medicoId))
                .ifPresent(otro -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta ya está vinculada a otro médico");
                });
        return user;
    }
}
