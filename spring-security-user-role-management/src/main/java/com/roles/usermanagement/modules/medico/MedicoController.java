package com.roles.usermanagement.modules.medico;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medicos")
@Tag(name = "Médicos")
@SecurityRequirement(name = "bearerAuth")
public class MedicoController {
    private final MedicoService service;

    public MedicoController(MedicoService service) {
        this.service = service;
    }

    @Operation(summary = "Listar médicos", description = "Directorio para elegir médico en ingresos y citas.")
    @GetMapping
    @PreAuthorize("hasAuthority('MEDICO_READ')")
    public List<MedicoResponse> todos() {
        return service.todos();
    }

    @Operation(summary = "Registrar médico", description = "username (opcional) vincula una cuenta con rol MEDICO.")
    @PostMapping
    @PreAuthorize("hasAuthority('MEDICO_MANAGE')")
    public ResponseEntity<MedicoResponse> crear(@Valid @RequestBody MedicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @Operation(summary = "Actualizar médico")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MEDICO_MANAGE')")
    public MedicoResponse actualizar(@PathVariable Long id, @Valid @RequestBody MedicoRequest request) {
        return service.actualizar(id, request);
    }
}
